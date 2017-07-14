package it.unibo.deis.lia.ramp.util;

import java.io.File;
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

	private static String HEAD = new String("StorageDate#StorageTime#Milliseconds#Type#Packet ID");
	// new String("Date#Time#Milliseconds#Type#Packet ID#Sender#Recipient");
	private static String BENCH_DIR = "./logs";
	private static String FILENAME = null;
	private static String FILE_EXTENSION = ".csv";
	private static String BENCH_PATH = null;
	private static String ENDS_WITH = "benchmark";

	private static CSVWriter CSV_WRITER = null;

	static {
		try {
			if (GeneralUtils.isAndroidContext()) {
				GeneralUtils.prepareAndroidContext();
				BENCH_DIR = android.os.Environment.getExternalStorageDirectory() + "/ramp/logs";
			} else {
				if (Files.notExists(Paths.get(BENCH_DIR), LinkOption.NOFOLLOW_LINKS)) {
					Files.createDirectories(new File(BENCH_DIR).toPath());
				}
			}

			String lastFile = getLastFilename(BENCH_DIR, ENDS_WITH, FILE_EXTENSION);
			if (lastFile == null) {
				CSV_WRITER = createFile();
			} else {
				// writer = new CSVWriter(new
				// FileWriter(DATE_TIME_FORMAT.format(date.getTime()) + "-" +
				// filename + ".csv"), ';');
				CSV_WRITER = new CSVWriter(new FileWriter(lastFile, true), ';');
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static synchronized CSVWriter createFile() {
		CSVWriter csvWriter = null;
		try {
			int nfiles = getNumberFiles(BENCH_DIR, ENDS_WITH, FILE_EXTENSION);
			if (nfiles == 0) {
				nfiles++;
			}
			FILENAME = getDate(DATE_FORMAT.toString()) + "-" + String.format("%02d", nfiles) + "-" + ENDS_WITH
					+ FILE_EXTENSION;
			BENCH_PATH = BENCH_DIR + "/" + FILENAME;

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
			csvWriter = new CSVWriter(new FileWriter(BENCH_PATH, true), ';');
			String[] entries = HEAD.split("#");
			csvWriter.writeNext(entries);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return csvWriter;
	}

	public static synchronized void append(long millis, String type, int packetId) {
		// String sender, String recipient
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Date date = new Date();
					SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
					SimpleDateFormat timeFormat = new SimpleDateFormat(TIME_FORMAT);
					String row = dateFormat.format(date.getTime()) + "#" + timeFormat.format(date.getTime()) + "#"
							+ millis + "#" + type + "#" + packetId;

					CSV_WRITER.writeNext(row.split("#"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public static synchronized String getLastFilename(String dirname, String endsWith, String fileExtension) {
		File[] files = null;
		try {
			File dir = new File(dirname);
			files = dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.toLowerCase().endsWith(endsWith + fileExtension);
				}
			});
			Arrays.sort(files, NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (files.length != 0)
			return files[files.length - 1].getPath();

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

	public static void closeCsvWriter() {
		try {
			if (CSV_WRITER != null) {
				CSV_WRITER.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static String getFileExtension(String filename) {
		if (filename.lastIndexOf(".") != -1 && filename.lastIndexOf(".") != 0)
			return filename.substring(filename.lastIndexOf(".") + 1);
		else
			return "";
	}

}
