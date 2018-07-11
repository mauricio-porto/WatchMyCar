
/*
 * Copyright (c) 2017 Nathanial Freitas / Guardian Project
 *  * Licensed under the GPLv3 license.
 *
 * Copyright (c) 2013-2015 Marco Ziccardi, Luca Bonato
 * Licensed under the MIT license.
 */


package com.braintech.watchmycar.sensors.media;


import android.content.Context;


public class MicrophoneTaskFactory {
	
	public static class RecordLimitExceeded extends Exception {

		/**
		 * 
		 */
		private static final long serialVersionUID = 7030672869928993643L;
		
	}
	
	private static MicSamplerTask samplerTask;

	public static MicSamplerTask makeSampler(Context context) throws RecordLimitExceeded {
		samplerTask = new MicSamplerTask();
		return samplerTask;
	}
	
	public static void pauseSampling() {
		if (samplerTask != null) {
			samplerTask.pause();
		}
	}
	
	public static void restartSampling() {
		if (samplerTask != null) {
			samplerTask.restart();
		}
	}
	
	public static boolean isSampling() {
		return samplerTask != null && samplerTask.isSampling();
	}
}
