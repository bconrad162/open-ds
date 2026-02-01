package main

import (
	"bytes"
	"encoding/binary"
	"errors"
	"log"
	"math"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/net/websocket"
)

const (
	wsAddr   = "127.0.0.1:5805"
	wsOrigin = "http://localhost"
)

type msg struct {
	Type    string `json:"type"`
	Team    string `json:"team,omitempty"`
	Value   string `json:"value,omitempty"`
	Name    string `json:"name,omitempty"`
	Index   int    `json:"index,omitempty"`
	FrcIndex int   `json:"frcIndex,omitempty"`
	Disabled bool  `json:"disabled,omitempty"`
	Axes    []float64 `json:"axes,omitempty"`
	Buttons []bool `json:"buttons,omitempty"`
	Mapping string `json:"mapping,omitempty"`
	Robot   string `json:"robot,omitempty"`
	Code    string `json:"code,omitempty"`
	Estop   string `json:"estop,omitempty"`
	Brownout string `json:"brownout,omitempty"`
	Enabled string `json:"enabled,omitempty"`
	Battery string `json:"battery,omitempty"`
	DsTx    string `json:"dsTx,omitempty"`
	Level   string `json:"level,omitempty"`
	Message string `json:"message,omitempty"`
	MatchTime string `json:"matchTime,omitempty"`
}

type bridge struct {
	mu      sync.Mutex
	clients map[*websocket.Conn]struct{}
	ds      *dsManager
}

func newBridge() *bridge {
	return &bridge{clients: make(map[*websocket.Conn]struct{})}
}

func (b *bridge) broadcast(m msg) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for c := range b.clients {
		_ = websocket.JSON.Send(c, m)
	}
}

func (b *bridge) addClient(c *websocket.Conn) {
	b.mu.Lock()
	b.clients[c] = struct{}{}
	b.mu.Unlock()
}

func (b *bridge) removeClient(c *websocket.Conn) {
	b.mu.Lock()
	delete(b.clients, c)
	b.mu.Unlock()
}

func (b *bridge) handler(ws *websocket.Conn) {
	b.addClient(ws)
	defer b.removeClient(ws)

	_ = websocket.JSON.Send(ws, msg{Type: "log", Level: "info", Message: "Bridge connected"})

	for {
		var m msg
		if err := websocket.JSON.Receive(ws, &m); err != nil {
			return
		}

		// TODO: hook these to DS protocol + joystick inputs.
		switch m.Type {
		case "hello":
			_ = websocket.JSON.Send(ws, msg{Type: "link", Value: "disconnected"})
		case "connect":
			b.broadcast(msg{Type: "log", Level: "info", Message: "connect requested: team " + m.Team})
			b.ensureDS().connect(m.Team)
		case "disconnect":
			b.broadcast(msg{Type: "log", Level: "info", Message: "disconnect requested"})
			b.ensureDS().disconnect()
		case "reconnect":
			b.ensureDS().reconnect()
		case "enable":
			b.ensureDS().setEnabled(true)
		case "disable":
			b.ensureDS().setEnabled(false)
			b.ensureDS().setEstop(false)
		case "estop":
			b.ensureDS().setEstop(true)
		case "gameData":
			b.ensureDS().setGameData(m.Value)
		case "mode":
			b.ensureDS().setMode(m.Value)
		case "alliance":
			b.ensureDS().setAlliance(m.Value)
		case "joystick":
			b.ensureDS().setJoystick(m)
		case "restartCode":
			b.ensureDS().restartCode.Store(true)
		case "restartRio":
			b.ensureDS().restartRio.Store(true)
		}
	}
}

func (b *bridge) ensureDS() *dsManager {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.ds == nil {
		b.ds = newDSManager(b.broadcast)
	}
	return b.ds
}

func main() {
	b := newBridge()
	server := &http.Server{
		Addr:              wsAddr,
		ReadHeaderTimeout: 5 * time.Second,
		Handler:           websocket.Handler(b.handler),
	}

	l, err := net.Listen("tcp", wsAddr)
	if err != nil {
		log.Fatalf("bridge listen failed: %v", err)
	}
	log.Printf("OpenDS bridge listening on %s", wsAddr)
	if err := server.Serve(l); err != nil {
		log.Fatalf("bridge server error: %v", err)
	}
}

type dsManager struct {
	broadcast func(msg)
	team      atomic.Value
	enabled   atomic.Bool
	estop     atomic.Bool
	restartCode atomic.Bool
	restartRio  atomic.Bool
	mode      atomic.Value
	gameData  atomic.Value
	alliance  atomic.Value
	joysticks sync.Map
	ntConn    net.Conn
	ntEntries sync.Map
	statsExtra sync.Map
	ntLast     sync.Map
	lastUdpSend atomic.Value

	connected atomic.Bool
	stopCh    chan struct{}
}

func newDSManager(broadcast func(msg)) *dsManager {
	m := &dsManager{broadcast: broadcast, stopCh: make(chan struct{})}
	m.mode.Store("teleop")
	m.gameData.Store("")
	return m
}

func (m *dsManager) connect(team string) {
	m.team.Store(strings.TrimSpace(team))
	if m.connected.CompareAndSwap(false, true) {
		m.stopCh = make(chan struct{})
		go m.run()
	}
}

func (m *dsManager) disconnect() {
	if m.connected.CompareAndSwap(true, false) {
		close(m.stopCh)
		m.statsExtra = sync.Map{}
		m.ntLast = sync.Map{}
		m.broadcast(msg{Type: "ntClear"})
		m.broadcast(msg{Type: "statsExtra", Value: ""})
	}
}

func (m *dsManager) reconnect() {
	team := loadString(m.team)
	m.disconnect()
	if team != "" {
		m.connect(team)
	}
}

func (m *dsManager) setEnabled(v bool) { m.enabled.Store(v) }
func (m *dsManager) setEstop(v bool)   { m.estop.Store(v) }
func (m *dsManager) setGameData(v string) {
	m.gameData.Store(v)
}

func (m *dsManager) setMode(v string) {
	if v == "" {
		return
	}
	m.mode.Store(v)
}

func (m *dsManager) setAlliance(v string) {
	if v == "" {
		return
	}
	m.alliance.Store(v)
}

func (m *dsManager) setJoystick(in msg) {
	idx := in.FrcIndex
	if idx < 0 || idx > 5 {
		idx = in.Index
	}
	m.joysticks.Store(idx, joystickState{
		index:   idx,
		name:    in.Name,
		axes:    in.Axes,
		buttons: in.Buttons,
		mapping: in.Mapping,
		disabled: in.Disabled,
	})
}

func (m *dsManager) run() {
	defer func() {
		m.broadcast(msg{Type: "link", Value: "disconnected"})
	}()
	for m.connected.Load() {
		select {
		case <-m.stopCh:
			return
		default:
		}

		addr, label := resolveAddress(loadString(m.team))
		if addr == "" {
			time.Sleep(500 * time.Millisecond)
			continue
		}
		m.broadcast(msg{Type: "link", Value: label})
		if err := m.session(addr); err != nil {
			m.broadcast(msg{Type: "log", Level: "warn", Message: "connection lost: " + err.Error()})
			time.Sleep(500 * time.Millisecond)
		}
	}
}

func (m *dsManager) session(addr string) error {
	udpTx, udpRx, err := openUDP(addr)
	if err != nil {
		return err
	}
	defer udpTx.Close()
	defer udpRx.Close()

	tcpConn, err := net.DialTimeout("tcp", net.JoinHostPort(addr, "1740"), 300*time.Millisecond)
	if err != nil {
		return err
	}
	defer tcpConn.Close()

	m.broadcast(msg{Type: "log", Level: "info", Message: "DS TCP connected to " + addr + ":1740"})
	go m.ntConnect(addr)

	errCh := make(chan error, 3)
	stop := make(chan struct{})
	go m.udpSendLoop(udpTx, stop, errCh)
	go m.udpRecvLoop(udpRx, stop, errCh)
		go m.tcpLoop(tcpConn, stop, errCh)

	select {
	case err := <-errCh:
		close(stop)
		return err
	case <-m.stopCh:
		close(stop)
		return errors.New("stopped")
	}
}

func (m *dsManager) ntConnect(addr string) {
	if m.ntConn != nil {
		_ = m.ntConn.Close()
	}
	m.ntLast = sync.Map{}
	m.broadcast(msg{Type: "ntClear"})
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(addr, "1735"), 300*time.Millisecond)
	if err != nil {
		m.broadcast(msg{Type: "ntStatus", Value: "Disconnected"})
		return
	}
	m.ntConn = conn
	m.broadcast(msg{Type: "ntStatus", Value: "Connected"})
	go m.ntLoop(conn)
}

func (m *dsManager) ntLoop(conn net.Conn) {
	defer func() {
		_ = conn.Close()
		m.broadcast(msg{Type: "ntStatus", Value: "Disconnected"})
	}()

	if _, err := conn.Write(buildNtClientHello("opends-bridge")); err != nil {
		return
	}
	_ = conn.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
	readAndParseNt(conn, &m.ntEntries, &m.ntLast, m.broadcast)
	if _, err := conn.Write([]byte{0x05}); err != nil {
		return
	}
	_ = conn.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
	readAndParseNt(conn, &m.ntEntries, &m.ntLast, m.broadcast)

	keepalive := time.NewTicker(100 * time.Millisecond)
	defer keepalive.Stop()
	buf := make([]byte, 4096)
	var carry []byte
	for {
		select {
		case <-keepalive.C:
			_, _ = conn.Write([]byte{0x00})
		default:
		}
		_ = conn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		n, err := conn.Read(buf)
		if err != nil {
			if nerr, ok := err.(net.Error); ok && nerr.Timeout() {
				continue
			}
			return
		}
		data := append(carry, buf[:n]...)
		carry = parseNtStream(data, &m.ntEntries, &m.ntLast, m.broadcast)
	}
}

func (m *dsManager) udpSendLoop(conn *net.UDPConn, stop <-chan struct{}, errCh chan<- error) {
	seq := uint16(0)
	count := 0
	ticker := time.NewTicker(20 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-stop:
			return
		case <-ticker.C:
			seq += 2
			count++
			station := allianceStation(loadString(m.alliance))
			restartCode := m.restartCode.Swap(false)
			restartRio := m.restartRio.Swap(false)
			packet := buildDsToRioUdp(seq, m.enabled.Load(), m.estop.Load(), loadString(m.mode), station, &m.joysticks, count <= 10, restartCode, restartRio)
			m.lastUdpSend.Store(time.Now())
			if _, err := conn.Write(packet); err != nil {
				errCh <- err
				return
			}
		}
	}
}

func (m *dsManager) udpRecvLoop(conn *net.UDPConn, stop <-chan struct{}, errCh chan<- error) {
	buf := make([]byte, 2048)
	lastSeen := time.Now()
	m.broadcast(msg{Type: "stats", Robot: "Disconnected", Code: "—", Estop: "—", Enabled: "—", Battery: "—"})
	for {
		select {
		case <-stop:
			return
		default:
		}
		_ = conn.SetReadDeadline(time.Now().Add(300 * time.Millisecond))
		n, _, err := conn.ReadFromUDP(buf)
		if err != nil {
			if nerr, ok := err.(net.Error); ok && nerr.Timeout() {
				if time.Since(lastSeen) > 600*time.Millisecond {
					m.broadcast(msg{Type: "stats", Robot: "Disconnected", Code: "—", Estop: "—", Enabled: "—", Battery: "—"})
				}
				continue
			}
			errCh <- err
			return
		}
		lastSeen = time.Now()
		if n >= 7 {
			bat := float64(uint8(buf[5])) + float64(uint8(buf[6]))/256.0
			status := buf[3]
			trace := buf[4]
		matchTime := ""
		if n >= 23 {
			// FMS-style match time (seconds) at bytes 20-21 if present
			mt := int(buf[20])<<8 | int(buf[21])
			matchTime = strconv.Itoa(mt)
		}
			estop := status&0x80 != 0
			brownout := status&0x10 != 0
			enabled := status&0x04 != 0
			codeInit := status&0x08 != 0
			robotCode := trace&0x20 != 0
			isRio := trace&0x10 != 0

			code := "—"
			if robotCode {
				code = "Running"
			} else if codeInit {
				code = "Initializing"
			}
			robot := "Simulated"
			if isRio {
				robot = "Connected"
			}
			m.broadcast(msg{
				Type:    "stats",
				Robot:   robot,
				Code:    code,
				Estop:   boolLabel(estop, "ESTOP", "OK"),
				Brownout: boolLabel(brownout, "Yes", "No"),
				Enabled: boolLabel(enabled, "Enabled", "Disabled"),
				Battery: formatFloat(bat),
				DsTx:    dsTxLabel(&m.lastUdpSend),
				MatchTime: matchTime,
			})
			parseRioUdpTags(buf[:n], &m.statsExtra, m.broadcast)
		}
	}
}

func (m *dsManager) tcpLoop(conn net.Conn, stop <-chan struct{}, errCh chan<- error) {
	defer conn.Close()
	_ = conn.SetReadDeadline(time.Time{})
	go func() {
		ticker := time.NewTicker(100 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-stop:
				return
			case <-ticker.C:
				payload := buildDsToRioTcp(loadString(m.gameData), &m.joysticks)
				if _, err := conn.Write(payload); err != nil {
					errCh <- err
					return
				}
			}
		}
	}()

	buf := make([]byte, 4096)
	var carry []byte
	for {
		select {
		case <-stop:
			return
		default:
		}
		n, err := conn.Read(buf)
		if err != nil {
			errCh <- err
			return
		}
		data := append(carry, buf[:n]...)
		carry = parseRioTcpTags(data, &m.statsExtra, m.broadcast)
	}
}

func buildDsToRioUdp(seq uint16, enabled bool, estop bool, mode string, station byte, sticks *sync.Map, includeDate bool, restartCode bool, restartRio bool) []byte {
	control := byte(0x00)
	if estop {
		control |= 0x80
	}
	if enabled {
		control |= 0x04
	}
	switch mode {
	case "auto":
		control |= 0x02
	case "test":
		control |= 0x01
	}

	request := byte(0x10) // DS_CONNECTED
	if restartCode {
		request |= 0x04
	}
	if restartRio {
		request |= 0x08
	}
	out := []byte{
		byte(seq >> 8),
		byte(seq & 0xFF),
		0x01, // comm version
		control,
		request,
		station,
	}
	if includeDate {
		out = append(out, buildUdpTag(0x0F, buildDatePayload())...)
		out = append(out, buildUdpTag(0x10, []byte(time.Now().Location().String()))...)
	}
	if enabled {
		for i := 0; i < 6; i++ {
			js := loadJoystick(sticks, i)
			out = append(out, buildUdpJoystickTag(js)...)
		}
	}
	return out
}

func buildDsToRioTcp(gameData string, sticks *sync.Map) []byte {
	var out []byte
	for i := 0; i < 6; i++ {
		js := loadJoystick(sticks, i)
		out = append(out, buildJoystickDescTag(js)...)
	}
	out = append(out, buildTcpTag(0x0E, []byte(gameData))...) // GAME_DATA
	out = append(out, buildTcpTag(0x1D, []byte{})...)         // DS_PING
	return out
}

func buildTcpTag(tag byte, payload []byte) []byte {
	length := len(payload) + 1
	out := []byte{byte(length >> 8), byte(length & 0xFF), tag}
	out = append(out, payload...)
	return out
}

func parseRioTcpTags(data []byte, stats *sync.Map, broadcast func(msg)) []byte {
	i := 0
	for i < len(data) {
		if len(data)-i < 2 {
			return data[i:]
		}
		// try 2-byte length format
		length := int(data[i])<<8 | int(data[i+1])
		if length > 0 && len(data)-i >= length+2 && len(data)-i >= 3 {
			tag := data[i+2]
			payload := data[i+3 : i+2+length]
			handleRioTag(tag, payload, stats, broadcast)
			i += 2 + length
			continue
		}
		// fallback 1-byte length format
		length = int(data[i])
		if length <= 0 || len(data)-i < length+1 || len(data)-i < 2 {
			return data[i:]
		}
		tag := data[i+1]
		payload := data[i+2 : i+1+length]
		handleRioTag(tag, payload, stats, broadcast)
		i += 1 + length
	}
	return nil
}

func handleRioTag(tag byte, payload []byte, stats *sync.Map, broadcast func(msg)) {
	switch tag {
	case 0x00: // RADIO_EVENTS
		if len(payload) > 0 {
			broadcast(msg{Type: "log", Level: "info", Message: string(bytes.TrimSpace(payload))})
		}
	case 0x01: // USAGE_REPORT
		if len(payload) > 0 {
			broadcast(msg{Type: "log", Level: "info", Message: "Usage: " + string(bytes.TrimSpace(payload))})
		}
	case 0x0B: // ERROR_MESSAGE
		level, text := parseErrorMessage(payload)
		broadcast(msg{Type: "log", Level: level, Message: text})
	case 0x0C: // STANDARD_OUT
		if len(payload) > 6 {
			text := string(bytes.TrimSpace(payload[6:]))
			if text != "" {
				broadcast(msg{Type: "log", Level: "info", Message: text})
			}
		}
	case 0x0A: // VERSION_INFO
		if len(payload) < 5 {
			return
		}
		devType := "Unknown"
		switch payload[0] {
		case 0:
			devType = "Software"
		case 2:
			devType = "CAN Talon"
		case 8:
			devType = "PDP"
		case 9:
			devType = "PCM"
		case 21:
			devType = "Pigeon"
		}
		strs := parseNLengthStrings(payload[4:], 1)
		name := ""
		version := ""
		if len(strs) > 0 {
			name = strs[0]
		}
		if len(strs) > 1 {
			version = strs[1]
		}
		if name != "" && version != "" {
			key := devType + " " + name
			if name == "roboRIO Image" {
				key = "RIO Version"
			} else if name == "FRC_Lib_Version" {
				key = "WPILib Version"
			}
			updateStat(stats, broadcast, key, version)
		}
	case 0x04: // DISABLE_FAULTS
		if len(payload) >= 4 {
			comms := readU16b(payload[0:2])
			v12 := readU16b(payload[2:4])
			updateStat(stats, broadcast, "Disable Faults Comms", strconv.Itoa(comms))
			updateStat(stats, broadcast, "Disable Faults 12V", strconv.Itoa(v12))
		}
	case 0x05: // RAIL_FAULTS (not fully defined in Java)
		if len(payload) >= 6 {
			v6 := readU16b(payload[0:2])
			v5 := readU16b(payload[2:4])
			v3 := readU16b(payload[4:6])
			updateStat(stats, broadcast, "Rail Faults 6V", strconv.Itoa(v6))
			updateStat(stats, broadcast, "Rail Faults 5V", strconv.Itoa(v5))
			updateStat(stats, broadcast, "Rail Faults 3.3V", strconv.Itoa(v3))
		}
	}
}

func parseErrorMessage(payload []byte) (string, string) {
	if len(payload) < 13 {
		return "warn", "error message (truncated)"
	}
	flagByte := payload[12]
	level := "warn"
	if flagByte&0x80 != 0 {
		level = "error"
	}
	strs := parseNLengthStrings(payload[13:], 2)
	details := ""
	location := ""
	stack := ""
	if len(strs) > 0 {
		details = strs[0]
	}
	if len(strs) > 1 {
		location = strs[1]
	}
	if len(strs) > 2 {
		stack = strs[2]
	}
	text := details
	if location != "" {
		text += " @ " + location
	}
	if stack != "" {
		text += " | " + stack
	}
	return level, text
}

func buildNtClientHello(identity string) []byte {
	payload := append([]byte{0x01, 0x03, 0x00}, encodeUlebString(identity)...)
	return payload
}

func readAndParseNt(conn net.Conn, entries *sync.Map, last *sync.Map, broadcast func(msg)) {
	buf := make([]byte, 2048)
	n, err := conn.Read(buf)
	if err != nil || n == 0 {
		return
	}
	parseNtStream(buf[:n], entries, last, broadcast)
}

func parseNtStream(data []byte, entries *sync.Map, last *sync.Map, broadcast func(msg)) []byte {
	i := 0
	for i < len(data) {
		msgType := data[i]
		if msgType == 0x00 {
			i++
			continue
		}
		used, ok := parseNtMessage(data[i:], entries, last, broadcast)
		if !ok {
			return data[i:]
		}
		i += used
	}
	return nil
}

type ntEntry struct {
	key   string
	typ   byte
	value string
}

func parseNtMessage(data []byte, entries *sync.Map, last *sync.Map, broadcast func(msg)) (int, bool) {
	if len(data) < 1 {
		return 0, false
	}
	msgType := data[0]
	idx := 1
	switch msgType {
	case 0x02: // Proto Unsup
		if idx+2 <= len(data) {
			ver := readU16b(data[idx : idx+2])
			broadcast(msg{Type: "ntStatus", Value: "Proto unsupported (" + strconv.Itoa(ver) + ")"})
		}
		idx += 2
		return idx, true
	case 0x10: // Entry Assign
		key, used, ok := readNtString(data[idx:])
		if !ok {
			return 0, false
		}
		idx += used
		if idx >= len(data) {
			return 0, false
		}
		typ := data[idx]
		idx++
		id, ok := readU16(data, idx)
		if !ok {
			return 0, false
		}
		idx += 2
		seq, ok := readU16(data, idx)
		if !ok {
			return 0, false
		}
		_ = seq
		idx += 2
		if idx >= len(data) {
			return 0, false
		}
		idx++ // persistent flag
		val, usedVal, ok := readNtValue(typ, data[idx:])
		if !ok {
			return 0, false
		}
		idx += usedVal
		entries.Store(id, ntEntry{key: key, typ: typ, value: val})
		emitNtEntry(last, broadcast, key, val)
	case 0x11: // Entry Update
		id, ok := readU16(data, idx)
		if !ok {
			return 0, false
		}
		idx += 2
		_, ok = readU16(data, idx)
		if !ok {
			return 0, false
		}
		idx += 2
		if idx >= len(data) {
			return 0, false
		}
		typ := data[idx]
		idx++
		val, usedVal, ok := readNtValue(typ, data[idx:])
		if !ok {
			return 0, false
		}
		idx += usedVal
		if entry, ok := entries.Load(id); ok {
			e := entry.(ntEntry)
			e.value = val
			e.typ = typ
			entries.Store(id, e)
			emitNtEntry(last, broadcast, e.key, val)
		}
	case 0x13: // Entry Delete
		id, ok := readU16(data, idx)
		if !ok {
			return 0, false
		}
		if entry, ok := entries.Load(id); ok {
			e := entry.(ntEntry)
			broadcast(msg{Type: "ntDelete", Value: e.key})
		}
		entries.Delete(id)
		idx += 2
	case 0x14: // Clear Entries
		if len(data) < idx+4 {
			return 0, false
		}
		entries.Range(func(k, v any) bool {
			entries.Delete(k)
			return true
		})
		broadcast(msg{Type: "ntClear"})
		idx += 4
	case 0x04: // Server Hello
		// serverFlag + identity string
		if idx >= len(data) {
			return 0, false
		}
		idx++
		identity, used, ok := readNtString(data[idx:])
		if !ok {
			return 0, false
		}
		if identity != "" {
			broadcast(msg{Type: "ntStatus", Value: "Connected (" + identity + ")"})
		}
		idx += used
	default:
		// Unknown message type; skip one byte to avoid infinite loop
		return 1, true
	}
	return idx, true
}

func emitNtEntry(last *sync.Map, broadcast func(msg), key, val string) {
	if last == nil {
		broadcast(msg{Type: "ntEntry", Value: key + " = " + val})
		return
	}
	prev, _ := last.Load(key)
	if prev == val {
		return
	}
	last.Store(key, val)
	broadcast(msg{Type: "ntEntry", Value: key + " = " + val})
}

func readNtValue(typ byte, data []byte) (string, int, bool) {
	switch typ {
	case 0x00: // boolean
		if len(data) < 1 {
			return "", 0, false
		}
		if data[0] == 0x01 {
			return "true", 1, true
		}
		return "false", 1, true
	case 0x01: // double
		if len(data) < 8 {
			return "", 0, false
		}
		val := mathFromBytes(data[:8])
		return strconv.FormatFloat(val, 'f', 4, 64), 8, true
	case 0x02: // string
		s, used, ok := readNtString(data)
		return s, used, ok
	case 0x10: // boolean array
		if len(data) < 1 {
			return "", 0, false
		}
		count := int(data[0])
		if len(data) < 1+count {
			return "", 0, false
		}
		return "bool[" + strconv.Itoa(count) + "]", 1 + count, true
	case 0x11: // double array
		if len(data) < 1 {
			return "", 0, false
		}
		count := int(data[0])
		need := 1 + 8*count
		if len(data) < need {
			return "", 0, false
		}
		return "double[" + strconv.Itoa(count) + "]", need, true
	case 0x12: // string array
		if len(data) < 1 {
			return "", 0, false
		}
		count := int(data[0])
		idx := 1
		for i := 0; i < count; i++ {
			_, used, ok := readNtString(data[idx:])
			if !ok {
				return "", 0, false
			}
			idx += used
		}
		return "string[" + strconv.Itoa(count) + "]", idx, true
	default:
		return "unsupported", 0, false
	}
}

func readNtString(data []byte) (string, int, bool) {
	n, nbytes, ok := decodeUleb(data)
	if !ok || len(data) < nbytes+n {
		return "", 0, false
	}
	start := nbytes
	end := start + n
	return string(data[start:end]), end, true
}

func decodeUleb(data []byte) (int, int, bool) {
	value := 0
	shift := 0
	for i := 0; i < len(data); i++ {
		b := data[i]
		value |= int(b&0x7F) << shift
		if (b & 0x80) == 0 {
			return value, i + 1, true
		}
		shift += 7
		if shift > 63 {
			return 0, 0, false
		}
	}
	return 0, 0, false
}

func encodeUlebString(s string) []byte {
	enc := encodeUleb(len(s))
	return append(enc, []byte(s)...)
}

func encodeUleb(n int) []byte {
	var out []byte
	v := n
	for {
		b := byte(v & 0x7F)
		v >>= 7
		if v != 0 {
			b |= 0x80
		}
		out = append(out, b)
		if v == 0 {
			break
		}
	}
	return out
}

func readU16(data []byte, idx int) (int, bool) {
	if len(data) < idx+2 {
		return 0, false
	}
	return (int(data[idx]) << 8) | int(data[idx+1]), true
}

func readU16b(data []byte) int {
	if len(data) < 2 {
		return 0
	}
	return (int(data[0]) << 8) | int(data[1])
}

func mathFromBytes(b []byte) float64 {
	if len(b) < 8 {
		return 0
	}
	bits := binary.BigEndian.Uint64(b)
	return math.Float64frombits(bits)
}

type joystickState struct {
	index   int
	name    string
	axes    []float64
	buttons []bool
	mapping string
	disabled bool
}

func loadJoystick(sticks *sync.Map, index int) joystickState {
	if sticks == nil {
		return joystickState{index: index}
	}
	if v, ok := sticks.Load(index); ok {
		return v.(joystickState)
	}
	return joystickState{index: index}
}

func buildUdpJoystickTag(js joystickState) []byte {
	var payload []byte
	if js.name == "" || js.disabled {
		payload = append(payload, 0, 0, 0)
		return buildUdpTag(0x0C, payload)
	}
	payload = append(payload, 6) // numAxes
	for i := 0; i < 6; i++ {
		val := 0
		if i < len(js.axes) {
			val = dblToInt8(js.axes[i])
		}
		payload = append(payload, byte(val))
	}
	btns := js.buttons
	payload = append(payload, byte(len(btns)))
	payload = append(payload, packBools(btns)...)
	payload = append(payload, 0) // povCount
	return buildUdpTag(0x0C, payload)
}

func buildJoystickDescTag(js joystickState) []byte {
	var payload []byte
	payload = append(payload, byte(js.index))
	if js.disabled {
		payload = append(payload, 0, 0, 0)
		return buildTcpTag(0x02, payload)
	}
	isXbox := byte(0)
	if js.mapping == "standard" {
		isXbox = 1
	}
	payload = append(payload, isXbox)
	frcType := byte(0x15) // HID_GAMEPAD
	payload = append(payload, frcType)
	name := js.name
	if name == "" {
		name = "Gamepad"
	}
	payload = append(payload, byte(len(name)))
	payload = append(payload, []byte(name)...)
	payload = append(payload, 6) // numAxes
	for i := 0; i < 6; i++ {
		payload = append(payload, byte(i%3))
	}
	payload = append(payload, byte(len(js.buttons)))
	payload = append(payload, 0) // povCount
	return buildTcpTag(0x02, payload)
}

func buildUdpTag(tag byte, payload []byte) []byte {
	length := len(payload) + 1
	out := []byte{byte(length), tag}
	out = append(out, payload...)
	return out
}

func dblToInt8(v float64) int {
	if v > 1 {
		v = 1
	}
	if v < -1 {
		v = -1
	}
	if v < 0 {
		return int(v * 128)
	}
	return int(v * 127)
}

func packBools(bools []bool) []byte {
	numBools := len(bools)
	packed := numBools >> 3
	if (numBools & 0x07) != 0 {
		packed++
	}
	out := make([]byte, packed)
	for i, v := range bools {
		if v {
			out[i>>3] |= 1 << (i & 0x07)
		}
	}
	flipped := make([]byte, len(out))
	for i := 0; i < len(out); i++ {
		flipped[i] = out[len(out)-i-1]
	}
	return flipped
}

func parseNLengthStrings(data []byte, nSize int) []string {
	var out []string
	i := 0
	n := -1
	for i < len(data) {
		if n == -1 {
			if nSize == 1 {
				n = int(data[i])
			} else if i+1 < len(data) {
				n = int(data[i])<<8 | int(data[i+1])
				i++
			}
			i++
			continue
		}
		if n <= 0 || i+n > len(data) {
			break
		}
		raw := data[i : i+n]
		out = append(out, filterASCII(raw))
		i += n
		n = -1
	}
	return out
}

func parseRioUdpTags(packet []byte, stats *sync.Map, broadcast func(msg)) {
	if len(packet) <= 8 {
		return
	}
	tagPacket := packet[8:]
	c := 0
	for c+1 < len(tagPacket) {
		size := int(tagPacket[c])
		if size <= 0 || c+1+size > len(tagPacket) {
			break
		}
		tag := tagPacket[c+1]
		payload := tagPacket[c+2 : c+1+size]
		switch tag {
		case 0x04: // DISK_INFO
			if len(payload) >= 8 {
				free := u32(payload[4:8])
				updateStat(stats, broadcast, "Disk Free", bytesHuman(free))
			}
		case 0x05: // CPU_INFO
			if len(payload) >= 1 {
				cpu := parseCpuPercent(payload)
				updateStat(stats, broadcast, "CPU %", formatPct(cpu))
			}
		case 0x06: // RAM_INFO
			if len(payload) >= 8 {
				free := u32(payload[4:8])
				updateStat(stats, broadcast, "RAM Free", bytesHuman(free))
			}
		case 0x0E: // CAN_METRICS
			if len(payload) >= 14 {
				util := f32(payload[0:4]) * 100
				updateStat(stats, broadcast, "CAN Util", formatPct(util))
				busOff := u32(payload[4:8])
				txFull := u32(payload[8:12])
				rxErr := int(payload[12])
				txErr := int(payload[13])
				updateStat(stats, broadcast, "CAN Bus Off", strconv.FormatUint(uint64(busOff), 10))
				updateStat(stats, broadcast, "CAN TX Full", strconv.FormatUint(uint64(txFull), 10))
				updateStat(stats, broadcast, "CAN RX Err", strconv.Itoa(rxErr))
				updateStat(stats, broadcast, "CAN TX Err", strconv.Itoa(txErr))
			}
		case 0x08: // PDP_LOG
			if len(payload) >= 4 {
				total := parsePdpTotalCurrent(payload)
				if total >= 0 {
					updateStat(stats, broadcast, "PDP Total Current", formatAmps(total))
				}
				updateStat(stats, broadcast, "PDP Voltage", strconv.Itoa(int(payload[len(payload)-2]))+" V")
				updateStat(stats, broadcast, "PDP Temperature", strconv.Itoa(int(payload[len(payload)-1]))+" C")
			}
		}
		c += size + 1
	}
}

func updateStat(stats *sync.Map, broadcast func(msg), key, value string) {
	if stats == nil {
		return
	}
	prev, _ := stats.Load(key)
	if prev == value {
		return
	}
	stats.Store(key, value)
	broadcast(msg{Type: "statsExtra", Value: key + ": " + value})
}

func u32(b []byte) uint32 {
	if len(b) < 4 {
		return 0
	}
	return binary.BigEndian.Uint32(b)
}

func f32(b []byte) float64 {
	if len(b) < 4 {
		return 0
	}
	return float64(math.Float32frombits(binary.BigEndian.Uint32(b)))
}

func parseCpuPercent(payload []byte) float64 {
	num := int(payload[0])
	if num <= 0 {
		return 0
	}
	c := 1
	total := 0.0
	for i := 0; i < num && c+15 <= len(payload); i++ {
		tCrit := f32(payload[c : c+4])
		tAbove := f32(payload[c+4 : c+8])
		tNorm := f32(payload[c+8 : c+12])
		tLow := f32(payload[c+12 : c+16])
		den := tCrit + tAbove + tNorm + tLow
		if den > 0 {
			total += (tCrit + (tAbove * 0.90) + (tNorm * 0.75) + (tLow * 0.25)) / den
		}
		c += 16
	}
	if num > 0 {
		total = (total / float64(num)) * 100
	}
	return total
}

func bytesHuman(v uint32) string {
	val := float64(v)
	unit := "B"
	for _, u := range []string{"KB", "MB", "GB"} {
		if val < 1024 {
			break
		}
		val /= 1024
		unit = u
	}
	return strconv.FormatFloat(val, 'f', 1, 64) + " " + unit
}

func formatPct(v float64) string {
	return strconv.FormatFloat(v, 'f', 1, 64) + "%"
}

func parsePdpTotalCurrent(payload []byte) float64 {
	if len(payload) < 4 {
		return -1
	}
	// Port currents packed as 10-bit values starting at payload[1]
	bits := make([]int, 0, (len(payload)-3)*8)
	for i := 1; i < len(payload)-3; i++ {
		b := payload[i]
		for j := 7; j >= 0; j-- {
			bits = append(bits, int((b>>j)&1))
		}
	}
	pdpNum := 0
	total := 0.0
	for bitCtr := 0; bitCtr <= len(bits)-10; bitCtr += 10 {
		val := 0
		for i := 0; i < 10; i++ {
			val = (val << 1) | bits[bitCtr+i]
		}
		portCurrent := float64(val) / 8.0
		total += portCurrent
		bitCtr += func() int {
			pdpNum++
			if pdpNum == 6 || pdpNum == 12 {
				return 4
			}
			return 0
		}()
	}
	return total
}

func filterASCII(b []byte) string {
	var sb strings.Builder
	for _, c := range b {
		if c > 31 && c < 127 {
			sb.WriteByte(c)
		}
	}
	return sb.String()
}

func resolveAddress(team string) (string, string) {
	team = strings.TrimSpace(team)
	if team == "" {
		return "", ""
	}
	if isIPv4(team) {
		return team, "Wi-Fi (" + team + ")"
	}
	teamNum, _ := strconv.Atoi(team)
	candidates := []string{}
	if teamNum > 0 {
		candidates = append(candidates, "roboRIO-"+team+"-FRC.local")
	}
	candidates = append(candidates, "172.22.11.2", "127.0.0.1")
	for _, host := range candidates {
		if tcpReachable(host, "1740", 200*time.Millisecond) {
			if host == "127.0.0.1" {
				return host, "Sim (localhost)"
			}
			if host == "172.22.11.2" {
				return host, "USB (172.22.11.2)"
			}
			return host, "Wi-Fi (" + host + ")"
		}
	}
	return "", ""
}

func tcpReachable(host, port string, timeout time.Duration) bool {
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(host, port), timeout)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}

func openUDP(host string) (*net.UDPConn, *net.UDPConn, error) {
	txAddr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(host, "1110"))
	if err != nil {
		return nil, nil, err
	}
	rxAddr, err := net.ResolveUDPAddr("udp", ":1150")
	if err != nil {
		return nil, nil, err
	}
	tx, err := net.DialUDP("udp", nil, txAddr)
	if err != nil {
		return nil, nil, err
	}
	rx, err := net.ListenUDP("udp", rxAddr)
	if err != nil {
		_ = tx.Close()
		return nil, nil, err
	}
	return tx, rx, nil
}

func isIPv4(s string) bool {
	parts := strings.Split(s, ".")
	if len(parts) != 4 {
		return false
	}
	for _, p := range parts {
		v, err := strconv.Atoi(p)
		if err != nil || v < 0 || v > 255 {
			return false
		}
	}
	return true
}

func loadString(v atomic.Value) string {
	if v.Load() == nil {
		return ""
	}
	return v.Load().(string)
}

func formatFloat(v float64) string {
	return strconv.FormatFloat(v, 'f', 2, 64) + " V"
}

func formatAmps(v float64) string {
	return strconv.FormatFloat(v, 'f', 1, 64) + " A"
}

func buildDatePayload() []byte {
	now := time.Now()
	micro := uint32(now.Nanosecond() / 1000)
	payload := make([]byte, 0, 11)
	buf := make([]byte, 4)
	binary.BigEndian.PutUint32(buf, micro)
	payload = append(payload, buf...)
	payload = append(payload, byte(now.Second()))
	payload = append(payload, byte(now.Minute()))
	payload = append(payload, byte(now.Hour()))
	payload = append(payload, byte(now.Day()))
	payload = append(payload, byte(int(now.Month())-1))
	payload = append(payload, byte(now.Year()-1900))
	return payload
}

func allianceStation(v string) byte {
	if v == "" {
		return 0
	}
	parts := strings.Split(v, ":")
	if len(parts) != 2 {
		return 0
	}
	color := parts[0]
	num, err := strconv.Atoi(parts[1])
	if err != nil || num < 1 || num > 3 {
		return 0
	}
	sidedZeroed := num - 1
	if color == "blue" {
		return byte(sidedZeroed + 3)
	}
	return byte(sidedZeroed)
}

func boolLabel(val bool, yes, no string) string {
	if val {
		return yes
	}
	return no
}

func dsTxLabel(last *atomic.Value) string {
	if last == nil || last.Load() == nil {
		return "—"
	}
	t := last.Load().(time.Time)
	age := time.Since(t)
	if age > 2*time.Second {
		return "Stale"
	}
	return "Active"
}
