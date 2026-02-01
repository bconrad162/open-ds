const bridgeState = document.getElementById("bridgeState");
const logOutput = document.getElementById("logOutput");
const teamNum = document.getElementById("teamNum");
const autoConnect = document.getElementById("autoConnect");
const linkLabel = document.getElementById("linkLabel");
const logInfo = document.getElementById("logInfo");
const logWarn = document.getElementById("logWarn");
const logError = document.getElementById("logError");
const gamepadList = document.getElementById("gamepadList");
const statRobot = document.getElementById("statRobot");
const statCode = document.getElementById("statCode");
const statEstop = document.getElementById("statEstop");
const statBrownout = document.getElementById("statBrownout");
const statEnabled = document.getElementById("statEnabled");
const statBattery = document.getElementById("statBattery");
const statDsTx = document.getElementById("statDsTx");
const statMatchTime = document.getElementById("statMatchTime");
const statExtra = document.getElementById("statExtra");
const statEntries = new Map();
const ntStatus = document.getElementById("ntStatus");
const ntOutput = document.getElementById("ntOutput");
const ntEntries = new Map();
const tabButtons = document.querySelectorAll(".tab-btn");
const tabPanels = document.querySelectorAll(".tab-panel");
let lastGamepadRender = 0;

let socket;
let reconnectTimer;
let gamepadTimer;
const gamepadState = new Map();
const gamepadConfig = new Map();
const joystickStore = loadJoystickConfig();

const storage = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem(key);
      return v === null ? fallback : v;
    } catch {
      return fallback;
    }
  },
  set(key, val) {
    try {
      localStorage.setItem(key, val);
    } catch {
      // ignore
    }
  },
};

function setActiveTab(name) {
  tabButtons.forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.tab === name);
  });
  tabPanels.forEach((panel) => {
    panel.classList.toggle("active", panel.dataset.tab === name);
  });
  storage.set("activeTab", name);
}

tabButtons.forEach((btn) => {
  btn.addEventListener("click", () => {
    setActiveTab(btn.dataset.tab);
  });
});

setActiveTab(storage.get("activeTab", "connection"));

function loadJoystickConfig() {
  try {
    const raw = storage.get("joystickConfig", "{}");
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

function saveJoystickConfig(cache) {
  storage.set("joystickConfig", JSON.stringify(cache));
}

function setBridgeOnline(online) {
  bridgeState.textContent = online ? "Bridge Online" : "Bridge Offline";
  bridgeState.classList.toggle("online", online);
  bridgeState.classList.toggle("offline", !online);
}

function appendLog(line) {
  logOutput.textContent += `${line}\n`;
  logOutput.scrollTop = logOutput.scrollHeight;
}

function connectBridge() {
  clearTimeout(reconnectTimer);
  socket = new WebSocket("ws://127.0.0.1:5805");

  socket.addEventListener("open", () => {
    setBridgeOnline(true);
    send({ type: "hello" });
    if (autoConnect.checked && teamNum.value.trim()) {
      send({ type: "connect", team: teamNum.value.trim() });
      sendAlliance();
      send({ type: "mode", value: document.getElementById("modeSelect").value });
    }
  });

  socket.addEventListener("close", () => {
    setBridgeOnline(false);
    reconnectTimer = setTimeout(connectBridge, 1500);
  });

  socket.addEventListener("message", (event) => {
    let msg;
    try {
      msg = JSON.parse(event.data);
    } catch {
      appendLog(event.data);
      return;
    }
    if (msg.type === "log") {
      if (
        (msg.level === "info" && !logInfo.checked) ||
        (msg.level === "warn" && !logWarn.checked) ||
        (msg.level === "error" && !logError.checked)
      ) {
        return;
      }
      appendLog(`[${formatTs()}] [${msg.level.toUpperCase()}] ${msg.message}`);
    } else if (msg.type === "link") {
      linkLabel.textContent = msg.value || "—";
    } else if (msg.type === "stats") {
      statRobot.textContent = msg.robot || "—";
      statCode.textContent = msg.code || "—";
      statEstop.textContent = msg.estop || "—";
      statBrownout.textContent = msg.brownout || "—";
      statEnabled.textContent = msg.enabled || "—";
      statBattery.textContent = msg.battery || "—";
      statDsTx.textContent = msg.dsTx || "—";
      statMatchTime.textContent = formatMatchTime(msg.matchTime);
    } else if (msg.type === "statsExtra") {
      if (!msg.value) {
        statEntries.clear();
        renderStatsExtra();
      } else {
        const split = msg.value.split(": ");
        if (split.length >= 2) {
          const key = split[0];
          const val = split.slice(1).join(": ");
          statEntries.set(key, val);
          renderStatsExtra();
        }
      }
    } else if (msg.type === "ntStatus") {
      ntStatus.textContent = msg.value || "Disconnected";
    } else if (msg.type === "ntEntry") {
      const split = msg.value.split(" = ");
      if (split.length >= 2) {
        const key = split[0];
        const val = split.slice(1).join(" = ");
        ntEntries.set(key, val);
        renderNt();
      }
    } else if (msg.type === "ntDelete") {
      ntEntries.delete(msg.value);
      renderNt();
    } else if (msg.type === "ntClear") {
      ntEntries.clear();
      renderNt();
    }
  });
}

logInfo.checked = storage.get("logInfo", "false") === "true";
logWarn.checked = storage.get("logWarn", "false") === "true";
logError.checked = storage.get("logError", "true") === "true";
autoConnect.checked = storage.get("autoConnect", "false") === "true";

logInfo.addEventListener("change", () => storage.set("logInfo", logInfo.checked));
logWarn.addEventListener("change", () => storage.set("logWarn", logWarn.checked));
logError.addEventListener("change", () => storage.set("logError", logError.checked));
autoConnect.addEventListener("change", () => storage.set("autoConnect", autoConnect.checked));

function send(payload) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

document.getElementById("connectBtn").addEventListener("click", () => {
  statEntries.clear();
  renderStatsExtra();
  storage.set("teamNum", teamNum.value.trim());
  send({ type: "connect", team: teamNum.value.trim() });
  sendAlliance();
  const mode = document.getElementById("modeSelect").value;
  send({ type: "mode", value: mode });
});

document.getElementById("disconnectBtn").addEventListener("click", () => {
  send({ type: "disconnect" });
  statEntries.clear();
  renderStatsExtra();
  ntEntries.clear();
  renderNt();
  storage.set("enabled", "false");
});

document.getElementById("reconnectBtn").addEventListener("click", () => {
  send({ type: "reconnect" });
  statEntries.clear();
  renderStatsExtra();
  ntEntries.clear();
  renderNt();
});

document.getElementById("enableBtn").addEventListener("click", () => {
  send({ type: "enable" });
  storage.set("enabled", "true");
});

document.getElementById("disableBtn").addEventListener("click", () => {
  send({ type: "disable" });
  storage.set("enabled", "false");
});

document.getElementById("estopBtn").addEventListener("click", () => {
  send({ type: "estop" });
});

document.getElementById("sendGameDataBtn").addEventListener("click", () => {
  const val = document.getElementById("gameData").value.trim();
  send({ type: "gameData", value: val });
  storage.set("gameData", val);
});

document.getElementById("restartCodeBtn").addEventListener("click", () => {
  send({ type: "restartCode" });
});

document.getElementById("restartRioBtn").addEventListener("click", () => {
  send({ type: "restartRio" });
});

document.getElementById("modeSelect").addEventListener("change", (e) => {
  send({ type: "mode", value: e.target.value });
  storage.set("mode", e.target.value);
});

function sendAlliance() {
  const color = document.getElementById("allianceColor").value;
  const station = document.getElementById("allianceStation").value;
  storage.set("allianceColor", color);
  storage.set("allianceStation", station);
  send({ type: "alliance", value: `${color}:${station}` });
}

document.getElementById("allianceColor").addEventListener("change", sendAlliance);
document.getElementById("allianceStation").addEventListener("change", sendAlliance);

document.getElementById("clearLogsBtn").addEventListener("click", () => {
  logOutput.textContent = "";
});

document.getElementById("ntClearBtn").addEventListener("click", () => {
  ntEntries.clear();
  renderNt();
});

document.getElementById("exportLogsBtn").addEventListener("click", () => {
  downloadText("opends-logs.txt", logOutput.textContent);
});

document.getElementById("exportStatsBtn").addEventListener("click", () => {
  const lines = [];
  lines.push(`Robot: ${statRobot.textContent}`);
  lines.push(`Code: ${statCode.textContent}`);
  lines.push(`EStop: ${statEstop.textContent}`);
  lines.push(`Enabled: ${statEnabled.textContent}`);
  lines.push(`Battery: ${statBattery.textContent}`);
  lines.push(`DS Tx: ${statDsTx.textContent}`);
  lines.push("");
  const keys = Array.from(statEntries.keys()).sort();
  keys.forEach((k) => lines.push(`${k}: ${statEntries.get(k)}`));
  downloadText("opends-stats.txt", lines.join("\n"));
});

document.getElementById("ntExportBtn").addEventListener("click", () => {
  const keys = Array.from(ntEntries.keys()).sort();
  const lines = keys.map((k) => `${k} = ${ntEntries.get(k)}`);
  downloadText("opends-nt.txt", lines.join("\n"));
});

function renderNt() {
  ntOutput.innerHTML = "";
  const groups = new Map();
  for (const key of ntEntries.keys()) {
    const parts = key.split("/");
    const group = parts.length > 1 ? parts[1] : "root";
    if (!groups.has(group)) groups.set(group, []);
    groups.get(group).push(key);
  }
  const groupNames = Array.from(groups.keys()).sort();
  for (const g of groupNames) {
    const header = document.createElement("div");
    header.className = "nt-entry";
    const title = document.createElement("div");
    title.className = "nt-key";
    title.textContent = `[${g}]`;
    header.appendChild(title);
    ntOutput.appendChild(header);
    const keys = groups.get(g).sort();
    for (const key of keys) {
      const row = document.createElement("div");
      row.className = "nt-entry";
      const k = document.createElement("div");
      k.className = "nt-key";
      k.textContent = key;
      const v = document.createElement("div");
      v.className = "nt-val";
      v.textContent = ntEntries.get(key);
      row.appendChild(k);
      row.appendChild(v);
      ntOutput.appendChild(row);
    }
  }
}

function renderStatsExtra() {
  statExtra.innerHTML = "";
  const keys = Array.from(statEntries.keys()).sort();
  for (const key of keys) {
    const row = document.createElement("div");
    row.className = "nt-entry";
    const k = document.createElement("div");
    k.className = "nt-key";
    k.textContent = key;
    const v = document.createElement("div");
    v.className = "nt-val";
    v.textContent = statEntries.get(key);
    row.appendChild(k);
    row.appendChild(v);
    statExtra.appendChild(row);
  }
}

function downloadText(filename, text) {
  const blob = new Blob([text], { type: "text/plain" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

function formatTs() {
  const now = new Date();
  return now.toLocaleTimeString();
}

function formatMatchTime(val) {
  if (!val) return "—";
  const num = Number(val);
  if (Number.isNaN(num)) return val;
  const m = Math.floor(num / 60);
  const s = Math.floor(num % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

function restoreSettings() {
  teamNum.value = storage.get("teamNum", "");
  const color = storage.get("allianceColor", "red");
  const station = storage.get("allianceStation", "1");
  document.getElementById("allianceColor").value = color;
  document.getElementById("allianceStation").value = station;
  document.getElementById("modeSelect").value = storage.get("mode", "teleop");
  document.getElementById("gameData").value = storage.get("gameData", "");
}

restoreSettings();

statExtra.textContent = "";

connectBridge();

function renderGamepads() {
  gamepadList.innerHTML = "";
  if (gamepadState.size === 0) {
    const empty = document.createElement("div");
    empty.className = "meta";
    empty.textContent = "No gamepads detected.";
    gamepadList.appendChild(empty);
    return;
  }
  for (const [idx, gp] of gamepadState.entries()) {
    const saved = joystickStore[gp.id] || {};
    const config = gamepadConfig.get(idx) || {
      frcIndex: saved.frcIndex ?? idx,
      disabled: saved.disabled ?? false,
      axisMap: saved.axisMap ?? [0, 1, 2, 3, 4, 5],
    };
    const card = document.createElement("div");
    card.className = "gamepad";
    const h = document.createElement("h3");
    h.textContent = `#${idx} ${gp.id}`;
    const meta = document.createElement("div");
    meta.className = "meta";
    meta.textContent = `axes=${gp.axes.length} buttons=${gp.buttons.length} mapping=${gp.mapping}`;
    const live = document.createElement("div");
    live.className = "meta";
    const pressedButtons = [];
    gp.buttons.forEach((b, i) => {
      if (b.pressed || b.value > 0.5) pressedButtons.push(i);
    });
    const axisPreview = gp.axes
      .slice(0, 6)
      .map((v) => v.toFixed(2))
      .join(", ");
    live.textContent = `pressed=${pressedButtons.length ? pressedButtons.join(", ") : "none"} axes=[${axisPreview}]`;
    const row = document.createElement("div");
    row.className = "row";
    const idxLabel = document.createElement("label");
    idxLabel.textContent = "USB Index";
    const idxSelect = document.createElement("select");
    for (let i = 0; i < 6; i++) {
      const opt = document.createElement("option");
      opt.value = String(i);
      opt.textContent = String(i);
      if (i === config.frcIndex) opt.selected = true;
      idxSelect.appendChild(opt);
    }
    idxSelect.addEventListener("change", () => {
      const next = { ...config, frcIndex: Number(idxSelect.value) };
      gamepadConfig.set(idx, next);
      joystickStore[gp.id] = next;
      saveJoystickConfig(joystickStore);
      pollGamepads();
    });
    const disLabel = document.createElement("label");
    const dis = document.createElement("input");
    dis.type = "checkbox";
    dis.checked = config.disabled;
    dis.addEventListener("change", () => {
      const next = { ...config, disabled: dis.checked };
      gamepadConfig.set(idx, next);
      joystickStore[gp.id] = next;
      saveJoystickConfig(joystickStore);
    });
    disLabel.appendChild(dis);
    disLabel.appendChild(document.createTextNode(" Disabled"));
    row.appendChild(idxLabel);
    row.appendChild(idxSelect);
    row.appendChild(disLabel);
    const axisMap = document.createElement("div");
    axisMap.className = "axis-map";
    const axisNames = ["X", "Y", "Z", "RX", "RY", "RZ"];
    axisNames.forEach((name, i) => {
      const wrap = document.createElement("div");
      const label = document.createElement("label");
      label.textContent = name;
      const sel = document.createElement("select");
      for (let a = 0; a < gp.axes.length; a++) {
        const opt = document.createElement("option");
        opt.value = String(a);
        opt.textContent = String(a);
        if (a === config.axisMap[i]) opt.selected = true;
        sel.appendChild(opt);
      }
      sel.addEventListener("change", () => {
        const next = { ...config, axisMap: [...config.axisMap] };
        next.axisMap[i] = Number(sel.value);
        gamepadConfig.set(idx, next);
        joystickStore[gp.id] = next;
        saveJoystickConfig(joystickStore);
      });
      wrap.appendChild(label);
      wrap.appendChild(sel);
      axisMap.appendChild(wrap);
    });
    card.appendChild(h);
    card.appendChild(meta);
    card.appendChild(live);
    card.appendChild(row);
    card.appendChild(axisMap);

    const inputWrap = document.createElement("div");
    inputWrap.className = "input-grid";

    const buttonsGrid = document.createElement("div");
    buttonsGrid.className = "button-grid";
    gp.buttons.forEach((b, i) => {
      const btn = document.createElement("div");
      btn.className = `btn-indicator${b.pressed || b.value > 0.5 ? " active" : ""}`;
      const fill = document.createElement("div");
      fill.className = "btn-fill";
      const pct = Math.max(0, Math.min(1, b.value));
      fill.style.width = `${pct * 100}%`;
      const label = document.createElement("div");
      label.className = "btn-label";
      label.textContent = `${i}`;
      const val = document.createElement("div");
      val.className = "btn-value";
      val.textContent = b.value.toFixed(2);
      btn.appendChild(fill);
      btn.appendChild(label);
      btn.appendChild(val);
      buttonsGrid.appendChild(btn);
    });

    const axisGrid = document.createElement("div");
    axisGrid.className = "axis-grid";
    gp.axes.forEach((val, i) => {
      const row = document.createElement("div");
      row.className = "axis-row";
      const label = document.createElement("span");
      label.className = "axis-label";
      label.textContent = `A${i}`;
      const bar = document.createElement("div");
      bar.className = "axis-bar";
      const fill = document.createElement("div");
      fill.className = "axis-fill";
      const pct = Math.max(0, Math.min(1, (val + 1) / 2));
      fill.style.width = `${pct * 100}%`;
      bar.appendChild(fill);
      const value = document.createElement("span");
      value.className = "axis-value";
      value.textContent = val.toFixed(2);
      row.appendChild(label);
      row.appendChild(bar);
      row.appendChild(value);
      axisGrid.appendChild(row);
    });

    inputWrap.appendChild(buttonsGrid);
    inputWrap.appendChild(axisGrid);
    card.appendChild(inputWrap);
    gamepadList.appendChild(card);
  }
}

function pollGamepads() {
  const pads = navigator.getGamepads ? navigator.getGamepads() : [];
  gamepadState.clear();
  const used = new Set();
  for (const gp of pads) {
    if (!gp) continue;
    const saved = joystickStore[gp.id] || {};
    const config = gamepadConfig.get(gp.index) || {
      frcIndex: saved.frcIndex ?? gp.index,
      disabled: saved.disabled ?? false,
      axisMap: saved.axisMap ?? [0, 1, 2, 3, 4, 5],
    };
    if (used.has(config.frcIndex)) {
      config.frcIndex = gp.index;
    }
    used.add(config.frcIndex);
    gamepadConfig.set(gp.index, config);
    const axes = (gp.axes || []).map((v) => (typeof v === "number" ? v : 0));
    const buttons = (gp.buttons || []).map((b) => ({
      pressed: !!b.pressed,
      value: typeof b.value === "number" ? b.value : b.pressed ? 1 : 0,
    }));
    gamepadState.set(gp.index, {
      id: gp.id || "Gamepad",
      axes,
      buttons,
      mapping: gp.mapping || "unknown",
    });
    const buttonBits = buttons.map((b) => b.pressed || b.value > 0.5);
    const axesMapped = [];
    for (let i = 0; i < 6; i++) {
      const src = config.axisMap[i] ?? i;
      axesMapped[i] = axes[src] ?? 0;
    }
    send({
      type: "joystick",
      index: gp.index,
      frcIndex: config.frcIndex,
      disabled: config.disabled,
      name: gp.id || "Gamepad",
      axes: axesMapped,
      buttons: buttonBits,
      mapping: gp.mapping || "unknown",
    });
  }
  const active = document.activeElement;
  if (active && gamepadList.contains(active) && active.tagName === "SELECT") {
    return;
  }
  const now = Date.now();
  if (now - lastGamepadRender > 150) {
    lastGamepadRender = now;
    renderGamepads();
  }
}

window.addEventListener("gamepadconnected", pollGamepads);
window.addEventListener("gamepaddisconnected", pollGamepads);

gamepadTimer = setInterval(pollGamepads, 50);

document.getElementById("reloadGamepadsBtn").addEventListener("click", pollGamepads);
