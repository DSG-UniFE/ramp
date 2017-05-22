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
	
	private String filename = "benchmark";
	private ArrayList<String> benchmarksList;
	private String bench_dir = "temp/benchmark";
	private static SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	private static SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	
	public Benchmark(String filename) {
		this.benchmarksList  = new ArrayList<String>();
		this.setFilename(filename);
		this.createDirectory();
		this.createFile();
		
	}
	
	private void createDirectory() { 
		if (RampEntryPoint.getAndroidContext() != null) {
			bench_dir = android.os.Environment.getExternalStorageDirectory() + "/benchmark";
			File dir = new File(bench_dir);
			if(!dir.exists())
				dir.mkdirs();
		}
	 }

	private void createFile() {
		Calendar date = Calendar.getInstance();
		CSVWriter writer;
		try {
//			writer = new CSVWriter(new FileWriter(DATE_TIME_FORMAT.format(date.getTime()) + "-" + filename + ".csv"), ';');
			String filenameSaved = bench_dir + filename + ".csv";
			writer = new CSVWriter(new FileWriter(filenameSaved, true), ';');
			// feed in your array (or convert your data to an array)
		    String[] entries = "Date#Time#Milliseconds#Type#Packet ID#Sender#Recipient".split("#");
		    writer.writeNext(entries);    
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
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
				e.printStackTrace();
			}
		}
	}
	
	public void appendBench(Calendar date, String packetId, String sender, String recipient) {
		// long millis = System.currentTimeMillis();
		String row = DATE_FORMAT.format(date.getTime()) + "#" + 
				TIME_FORMAT.format(date.getTime()) + "#" +
				packetId + "#" +
				sender + "#" +
				recipient;
		
		CSVWriter writer;
		try {
			String filenameSaved = bench_dir + filename + ".csv";
			writer = new CSVWriter(new FileWriter(filenameSaved, true), ';');
	    	writer.writeNext(row.split("#"));
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public void addBenchmark(Calendar date, long millis, String type, 
			String packetId, String sender, String recipient) {
		// long millis = System.currentTimeMillis();
		String row = DATE_FORMAT.format(date.getTime()) + "#" + 
				TIME_FORMAT.format(date.getTime()) + "#" +
				millis + "#" +
				type + "#" +
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
