package com.mambu.apisdk;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Formats date classes into the ISO standard
 * 
 * @author edanilkis
 * 
 */
public class DateUtils {

	public static String DATE_FORMAT = "yyyy-MM-dd";

	public static SimpleDateFormat FORMAT = new SimpleDateFormat(DATE_FORMAT);

	public static String format(Date date) {
		return FORMAT.format(date);
	}
}