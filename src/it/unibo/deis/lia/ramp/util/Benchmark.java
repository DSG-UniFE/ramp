package it.unibo.deis.lia.ramp.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.io.comparator.NameFileComparator;

import com.opencsv.CSVWriter;

import it.unibo.deis.lia.ramp.RampEntryPoint;

public class Benchmark {

	private static String DATE_TIME_FORMAT = new String("yyyy-MM-dd HH:mm:ss");
	private static String DATE_FORMAT = new String("yyyy_MM_dd");
	private static String TIME_FORMAT = new String("HH:mm:ss");

	private static String HEAD = new String("Date#Time#Milliseconds#Type#Packet ID#Sender#Recipient");
	private static String BENCH_DIR = "./logs/";
	private static String FILENAME = null;
	private static String FILE_EXTENSION = ".csv";
	private static String BENCH_PATH = null;
	private static String ENDS_WITH = "benchmark";

	public static void createDirectory() {
		try {
			if (RampEntryPoint.getAndroidContext() != null) {
				System.out.println("--->SONO DENTRO");
				if (android.os.Environment.getExternalStorageDirectory() != null) {
					BENCH_DIR = android.os.Environment.getExternalStorageDirectory() + "/ramp/";

					File dir = new File(BENCH_DIR);
					if (!dir.exists())
						dir.mkdirs();

					// This prevents media scanner from reading your media files
					// and providing them to other apps through the MediaStore
					// content provider.
					File file = new File(android.os.Environment.getExternalStorageDirectory() + "/ramp/.nomedia");
					if (!file.exists()) {
						try {
							FileOutputStream out = new FileOutputStream(file);
							out.flush();
							out.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					BENCH_DIR = android.os.Environment.getExternalStorageDirectory() + "/ramp/logs/";
					dir = new File(BENCH_DIR);
					if (!dir.exists())
						dir.mkdir();
				}
			} else {
				if (Files.notExists(Paths.get(BENCH_DIR), LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(new File(BENCH_DIR).toPath());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void createFile() {
		createDirectory();

		int nfiles = getNumberFiles(BENCH_DIR, ENDS_WITH, FILE_EXTENSION);
		FILENAME = getDate(DATE_FORMAT.toString()) + "-" + String.format("%02d", nfiles) + "-" + ENDS_WITH
				+ FILE_EXTENSION;
		BENCH_PATH = BENCH_DIR + FILENAME;

		System.out.println("FILENAME: " + FILENAME);
		System.out.println("BENCH_DIR: " + BENCH_DIR);
		System.out.println("BENCH_PATH: " + BENCH_PATH);

		if (RampEntryPoint.getAndroidContext() != null) {
			File benchFile = new File(BENCH_PATH);
			if (!benchFile.exists()) {
				try {
					benchFile.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		CSVWriter writer;
		try {
			// writer = new CSVWriter(new
			// FileWriter(DATE_TIME_FORMAT.format(date.getTime()) + "-" +
			// filename + ".csv"), ';');
			writer = new CSVWriter(new FileWriter(BENCH_PATH, true), ';');
			String[] entries = HEAD.split("#");
			writer.writeNext(entries);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void appendBenchmark(int millis, String type, String packetId, String sender, String recipient) {
		File lastFile = getLastFilename(BENCH_DIR, ENDS_WITH, FILE_EXTENSION);
		if (lastFile == null) {
			createFile();
			lastFile = getLastFilename(BENCH_DIR, ENDS_WITH, FILE_EXTENSION);
		}

		Date date = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
		SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT);
		String row = dateFormat.format(date.getTime()) + "#" + timeFormat.format(date.getTime()) + "#" + millis + "#"
				+ type + "#" + packetId + "#" + sender + "#" + recipient;

		CSVWriter writer;
		try {
			writer = new CSVWriter(new FileWriter(lastFile, true), ';');
			writer.writeNext(row.split("#"));
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static File getLastFilename(String dirname, String endsWith, String fileExtension) {
		File dir = new File(dirname);
		File[] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(endsWith + fileExtension);
			}
		});

		Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);

		if (files.length != 0)
			return files[files.length - 1];

		return null;
	}

	public static int getNumberFiles(String dirname, String endsWith, String fileExtension) {
		File dir = new File(dirname);
		File[] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(endsWith + fileExtension);
			}
		});

		Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);

		return files.length;
	}

	public static String getDate(String format) {
		// example "HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss" ecc...
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		return dateFormat.format(new Date());
	}

	private static String getFileExtension(String filename) {
		if (filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
			return filename.substring(filename.lastIndexOf(".") + 1);
		else
			return "";
	}
}
