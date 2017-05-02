package it.unibo.deis.lia.ramp.util;

import it.unibo.deis.lia.ramp.RampEntryPoint;

import java.io.File;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogWithTimestamp extends PrintStream {
	
	// read properties to configure the logger
	private static boolean logOnConsole = Boolean.parseBoolean(RampEntryPoint.getRampProperty("logOnConsole"));;
	private static boolean logOnFile = Boolean.parseBoolean(RampEntryPoint.getRampProperty("logOnFile"));
	private static String lineSeparator = System.getProperty("line.separator") != null ? System.getProperty("line.separator") : "\n";
	
	private Logger logger;
	private static final String defaultLogsFolder = "./logs";
	private static final String defaultLogsFileName = defaultLogsFolder + "/ramp.";
	
	public LogWithTimestamp(PrintStream out, String loggerName){
		super(out);
		// read properties and configure the logger
		if(logOnFile){ // log on rotated log files as well
			logger = Logger.getLogger("RAMP.logger." + loggerName);
			logger.setUseParentHandlers(false);
			FileHandler handler = null;
			try {
				File logsFolder = new File(defaultLogsFolder);
				if(!logsFolder.exists())
					logsFolder.mkdir();
				handler = new FileHandler(defaultLogsFileName + loggerName, 50 * 1024 * 1024, 4, true); // log rotation (4 files, 50MB each)
				handler.setLevel(Level.INFO);
				handler.setEncoding("UTF-8");
				handler.setFormatter(new LogFormatter());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(logger != null && handler != null){
				logger.addHandler(handler);
			}else{
				logOnFile = false;
			}
		}
	}
	
	@Override
	public void println(String s) {
		print(s + lineSeparator);
	}
	
	@Override
	public void print(String s) {
		String messageToLog = "[" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.FULL).format(new Date()) + "]: " + s;
		if(logOnConsole)
			super.print(messageToLog);
		if(logOnFile)
			logger.log(Level.INFO, messageToLog);
	}
	
	private class LogFormatter extends SimpleFormatter {
		
		@Override
		public synchronized String formatMessage(LogRecord record) {
			return this.format(record);
		}
		
		@Override
		public synchronized String format(LogRecord record) {
			return record.getMessage();
		}
		
	}

}