# OpenDS
<a href="https://github.com/Boomaa23/open-ds/actions/workflows/build-java.yml"><img src="https://github.com/Boomaa23/open-ds/actions/workflows/build-java.yml/badge.svg" /></a>
<a href="https://github.com/Boomaa23/open-ds/actions/workflows/checkstyle.yml"><img src="https://github.com/Boomaa23/open-ds/actions/workflows/checkstyle.yml/badge.svg" /></a>
<a href="https://github.com/Boomaa23/open-ds/actions/workflows/build-native.yml"><img src="https://github.com/Boomaa23/open-ds/actions/workflows/build-native.yml/badge.svg" /></a>
<a href="https://github.com/Boomaa23/open-ds/releases/latest"><img src="https://img.shields.io/github/v/release/Boomaa23/open-ds" /></a>

A reverse-engineered lightweight FRC Driver Station alternative for Windows, Linux, and macOS

Download [here](https://github.com/bconrad162/open-ds/releases/download/4.0.0/OpenDS.jar) ([JDK/JRE 8+](https://www.oracle.com/java/technologies/downloads/#jdk21) required).

Copyright (C) 2020-2025 Boomaa23


## Features
OpenDS is a fully functional FIRST Robotics Competition (FRC) Driver Station 
alternative for Windows, Linux, and macOS systems.
All the features of the official Driver Station are implemented in OpenDS, 
meaning teams can use it in the place of the official Driver Station 
when testing robot features away from the competition.

OpenDS is extremely lightweight (about 1 MB) and does not require an 
installation of any kind, unlike the official Driver Station which 
has a lengthy installation process and heavy install footprint.

NOTE: OpenDS may not be used during FRC-legal competitions as per 
rules R710 and R901 (previously R66 and R88). 
OpenDS is intended for testing use only.

* Robot
    * Enable and disable
    * Change mode (teleop/auto/test)
    * Change alliance station (1/2/3 & red/blue)
    * Send game data
    * Change team number
    * USB Joystick and Xbox controller input support
    * Restart robot code and RoboRIO
    * Emergency stop
* Statistics
    * Robot voltage
    * Connections
    * Brownouts
    * Match time left (FMS)
    * CAN Bus
    * RoboRIO disk/RAM/CPU/version
    * Disable/Rail faults
    * Logging to `.dslog` files
* NetworkTables
    * Read Shuffleboard and SmartDashboard packets
    * Display NetworkTables passed data
* FMS
    * Connect to a offseason FMS or Cheesy Arena
    * Choose to connect or not
* Support
    * Lightweight executable
    * Windows, Linux, and macOS support
    * No install prerequisites
    * Easily modifiable for updated protocol years
    * Command-line (CLI) parameters
    

## Setup
Download the stable jar from [here](https://github.com/bconrad162/open-ds/releases/download/4.0.0/OpenDS.jar) and run. There are no prerequisites besides having a Java installation with JRE 8 or newer (the JRE is included with any installation of the same JDK version).

### Get Java (JRE/JDK 8+)
If you are not sure whether Java is installed, open a terminal/command prompt and run:
```bash
java -version
```
If that prints a version number (like `1.8.x` or `11.x`), you are good.

If Java is missing, install a JDK (it includes the JRE):
* Recommended: [Oracle JDK 25](https://www.oracle.com/java/technologies/downloads/#jdk21)

### Run the jar from the command line
After downloading `OpenDS.jar`, open a terminal/command prompt in the same folder and run:
```bash
java -jar OpenDS.jar
```
If you want extra logs, add `--debug`:
```bash
java -jar OpenDS.jar --debug
```


### Troubleshooting
If you run into issues, ensure that you are running a 64-bit installation of either Windows 7/8.1/10/11, Linux kernel version 2.6.35 or greater, or macOS 10 (OSX) or newer.

Try launching from the command line (`java -jar open-ds.jar`) and observing the console output for additional details. You can also launch with debug (`--debug`) to print more information to the console.

If you are using the WPILib simulator (instead of a physical robot), ensure you have the following line in your `build.gradle` (or equivalent in `build.gradle.kts`).
```groovy
wpi.sim.addDriverstation().defaultEnabled = true
```

If issues persist, please report them on the "Issues" section of the GitHub [here](https://github.com/Boomaa23/open-ds/issues) and they will be resolved as soon as possible.


## License
OpenDS may be used without restriction for the purpose of testing robots by teams and individuals.

See [LICENSE.txt](https://github.com/Boomaa23/open-ds/blob/master/LICENSE.txt) for more details.


## Contributing
If you find a bug or issue with OpenDS, please report it on the "Issues" section of the GitHub [here](https://github.com/Boomaa23/open-ds/issues).

For protocol changes in future years, OpenDS is easily modifiable. Ports, IP addresses, display layouts, and packet creation/parsing are all re-formattable.


## Acknowledgements
Thank you to Jessica Creighton and Alex Spataru for their work on the FRCture documentation and LibDS respectively.
