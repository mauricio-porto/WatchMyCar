# WatchMyCar

**Warning: This is a WIP (Work In Progress) project**

## Overview

*WatchMyCar* is a surveillance Android application that uses some device sensors to detect intrusion in your car and throws a very loud external siren to keep thieves away. Besides the device sensors, you can add external sensors thru the Arduino module.

The primary component is a background service that receives sensor notifications when they exceed a set threshold. Once an intrusion is detected, the external siren is triggered and alert messages are sent to the registered phones.

An external module consists of an Arduino, the strong siren and some extra sensors. The communication between the Arduino and the Android phone is via Bluetooth.

The idea is partially based on the [Haven](https://github.com/guardianproject/haven) application.