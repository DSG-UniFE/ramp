package it.unibo.deis.lia.ramp.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import com.opencsv.CSVWriter;

import it.unibo.deis.lia.ramp.RampEntryPoint;

public class Benchmark {
	
	private String filename;
	private ArrayList<String> benchmarksList;
	private static SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	private static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	
	public Benchmark(String filename) {
		this.benchmarksList  = new ArrayList<String>();
		this.setFilename(filename);
		this.createDirectory();
		
	}
	
	private void createDirectory() { 
		if (RampEntryPoint.getAndroidContext() != null) {
			File androidShareDirectory = new File(android.os.Environment.getExternalStorageDirectory() + "/ramp");
			if(!androidShareDirectory.exists())
				androidShareDirectory.mkdirs();
			
			String logDirectory = androidShareDirectory.getAbsolutePath() + "/benchmark";
		    File dir = new File(logDirectory);
		    if(!dir.exists())
		    	dir.mkdir();
		}
	 }

	public void writeFile() {
		if (RampEntryPoint.getAndroidContext() != null) {
			Calendar date = Calendar.getInstance();
		       
			CSVWriter writer;
			try {
				writer = new CSVWriter(new FileWriter(DATE_TIME_FORMAT.format(date.getTime()) + filename + ".csv"), ';');
				// feed in your array (or convert your data to an array)
			    String[] entries = "Date#Time#Packet ID#Sender#Recipient".split("#");
			    writer.writeNext(entries);
			    
			    for (String temp : getBenchmarks()) {
			    	writer.writeNext(temp.split("#"));
				}
			    
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public void addBenchmark(Calendar date, String packetId, String sender, String recipient) {
		String row = DATE_FORMAT.format(date.getTime()) + "#" + 
				TIME_FORMAT.format(date.getTime()) + "#" +
				packetId + "#" +
				sender + "#" +
				recipient;
		this.benchmarksList.add(row);
	}
	
	public void removeLastBenchmark() {
		this.benchmarksList.remove(benchmarksList.size() - 1);
	}
	
	public ArrayList<String> getBenchmarks() {
		return benchmarksList;
	}
}
