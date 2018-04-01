#include "Arduino.h"
#include <NewPing.h>

#define TRIGGER_PIN_1  6
#define ECHO_PIN_1     5

#define MAX_DISTANCE 300 // Maximum distance we want to ping for (in centimeters). Maximum sensor distance is rated at 400-500cm.
#define DELAY 1000

int pinSiren = 4;
int pinLED = 13;

const int TOLERANCE = 15;	// (in centimeters)

unsigned int minSensor1Dist;
unsigned int maxSensor1Dist;

boolean armed = false;
boolean ledOn = false;
boolean triggered = false;
unsigned long trigger_time;
int rpt = 0;
const int RS = 4;		// Repeat Siren
const unsigned long SD = 60 * 1000L;	// Siren Duration
const unsigned long SI = 30 * 1000L;	// Siren Interval

NewPing sensor1(TRIGGER_PIN_1, ECHO_PIN_1, MAX_DISTANCE);

void measureDistances() {
	delay(DELAY);

	unsigned int uS_1 = sensor1.ping();
	unsigned int distCm_1 = uS_1 / US_ROUNDTRIP_CM;

	minSensor1Dist = distCm_1 > TOLERANCE ? distCm_1 - TOLERANCE : 0;
	maxSensor1Dist = distCm_1 + TOLERANCE;
}

void pull_the_trigger() {
	digitalWrite(pinSiren, HIGH);   // toca sirene
	trigger_time = millis();
	rpt = 1;
	triggered = true;
}

void setup() {
	Serial.begin(9600); // Open serial monitor at 9600 baud to send info via Bluetooth.
	pinMode(pinSiren, OUTPUT);
	digitalWrite(pinSiren, LOW);
}

void loop() {
	unsigned int uS = sensor1.ping(); // Send ping, get ping time in microseconds (uS).
	unsigned int distCm = uS / US_ROUNDTRIP_CM;

	if (Serial.available() > 0) {
		char c = Serial.read();

		if (c == 'A') {
			measureDistances();
			armed = true;
			goto bailout;
		}
		if (c == 'G') {
			pull_the_trigger();
			goto bailout;
		}
		if (c == 'D') {
			digitalWrite(pinSiren, LOW);   // desliga sirene
			armed = false;
			triggered = false;
			goto bailout;
		}
	}

	if (armed) {
		if (distCm > maxSensor1Dist || distCm < minSensor1Dist) {

			Serial.print("  "); // Lets give something to "wake-up" the receiving buffer
			Serial.println('T'); // THIEF!!!!

			pull_the_trigger();
		}

		if (triggered) {
			unsigned long now = millis();

			if (now < (trigger_time + rpt * SD + (rpt - 1) * SI)) {
				ledOn = !ledOn;
				ledOn ? digitalWrite(pinLED, LOW) : digitalWrite(pinLED, HIGH);

				digitalWrite(pinSiren, HIGH);
			} else if (now < trigger_time + rpt * SD + rpt * SI) {
				digitalWrite(pinLED, LOW);

				digitalWrite(pinSiren, LOW);
			} else {
				rpt++;
			}
		}
	}
	bailout: delay(DELAY);
}
