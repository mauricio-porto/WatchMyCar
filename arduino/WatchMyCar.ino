#include "Arduino.h"
#include <NewPing.h>

#define TRIGGER_PIN_1  12
#define ECHO_PIN_1     11
#define TRIGGER_PIN_2  10
#define ECHO_PIN_2      9

#define MAX_DISTANCE 300 // Maximum distance we want to ping for (in centimeters). Maximum sensor distance is rated at 400-500cm.
#define DELAY 100

int pinSiren = 4;

const float minTolerance = 0.8;
const float maxTolerance = 1.2;

int sensor1Dist;
int minSensor1Dist;
int maxSensor1Dist;

int sensor2Dist;
int minSensor2Dist;
int maxSensor2Dist;

boolean armed = false;

NewPing sensor1(TRIGGER_PIN_1, ECHO_PIN_1, MAX_DISTANCE);
NewPing sensor2(TRIGGER_PIN_2, ECHO_PIN_2, MAX_DISTANCE);

void setup() {
  Serial.begin(9600); // Open serial monitor at 9600 baud to send info via Bluetooth.
  pinMode(pinSiren, OUTPUT);
}

void loop() {
  unsigned int uS_1 = sensor1.ping(); // Send ping, get ping time in microseconds (uS).
  unsigned int distCm_1 = uS_1 / US_ROUNDTRIP_CM;

  unsigned int uS_2 = sensor2.ping(); // Send ping, get ping time in microseconds (uS).
  unsigned int distCm_2 = uS_2 / US_ROUNDTRIP_CM;

  if (Serial.available() > 0) {
    char c = Serial.read();
    if (c == 'A') {
    	sensor1Dist = distCm_1;
    	minSensor1Dist = sensor1Dist * minTolerance;
    	maxSensor1Dist = sensor1Dist * maxTolerance;
    	sensor2Dist = distCm_2;
    	minSensor2Dist = sensor2Dist * minTolerance;
    	maxSensor2Dist = sensor2Dist * maxTolerance;
    	armed = true;
    	goto bailout;
    }
    if (c == 'G') {
    	digitalWrite(pinSiren, HIGH);   // toca sirene
    	goto bailout;
    }
  }

  if (armed) {
    if (distCm_1 > maxSensor1Dist || distCm_1 < minSensor1Dist
     || distCm_2 > maxSensor2Dist || distCm_2 < minSensor2Dist) {
    	digitalWrite(pinSiren, HIGH);	// toca sirene
		Serial.print("  "); // Lets give something to "wake-up" the receiving buffer
		Serial.println('T'); // THIEF!!!!
    }
  }
bailout:
  delay(DELAY);
}
