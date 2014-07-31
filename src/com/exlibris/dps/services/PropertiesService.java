package com.exlibris.dps.services;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * PropertiesService is a wrapper to global.properties
 */
public class PropertiesService {

	private Properties properties = null;

	public static final String DELIVERY_SERVER = "delivery_server";

	private void init() {
		// Get the inputStream
		InputStream inputStream = this.getClass().getClassLoader()
				.getResourceAsStream("properties/global.properties");

		properties = new Properties();

		// load the inputStream using the Properties
		try {
			properties.load(inputStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public String get(String key) {
		if (properties == null)
			init();
		return properties.getProperty(key);
	}

}