package cn.edu.xmut.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * 
 */

public class RouteSetUtil {

	static ArrayList<String> lines = new ArrayList<String>();
	static ArrayList<String> rs = new ArrayList<String>();

	static String desnationString = "E:\\project\\p16\\model4\\";
	static String path = "E:\\project\\p16\\";

	public static void main(String[] args) {

		BufferedReader in = null;
		Map<String,ArrayList> upstreamLookupTable=new HashMap<String,ArrayList>();
		try {
			in = new BufferedReader(
					new FileReader(desnationString + "fig.fig"));
			String line;

			while ((line = in.readLine()) != null) {
				//System.out.println(line);
				lines.add(line);

			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		//从最下游子流域开始

		for (int i = lines.size() - 1; i >= 0; i--) {
			String line = lines.get(i);
			if (line.startsWith("route")) {
				//System.out.println(line);
				route(line, "-1");
				break;

			}
		}

		try {
			File file4 = new File(path + "routeset.txt");
			FileWriter fileWriter = new FileWriter(file4);
			IOUtils.writeLines(rs, null, fileWriter);
			IOUtils.closeQuietly(fileWriter);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static int route(String line, String k) {
		int lv = 0;
		lv++;
		String tokens[] = line.split("\\s+");
		//if(k!="-1")
		//   System.out.println(tokens[3]+","+k);
		String lineString = fineCommandByPosition(tokens[4]);
		String[] tt = lineString.split("\\s+");
		int code = Integer.parseInt(tt[1]);
		if (code == 5)
			lv = lv + add(lineString, tokens[3], lv);
		System.out.println(tokens[3] + "," + k + "," + lv);
		rs.add(tokens[3] + "," + k + "," + lv);
		return lv;

	}

	public static String fineCommandByPosition(String store) {
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String tokens[] = line.split("\\s+");
			if (tokens.length >= 3) {
				if (tokens[2].equals(store)) {
					return line;
				}
			}

		}
		return "";

	}

	public static int add(String line, String k, int lv) {
		int z = lv;
		int t1 = lv, t2 = lv, t3 = lv, t4 = lv;
		String tokens[] = line.split("\\s+");
		if (tokens.length >= 3) {
			String command1 = fineCommandByPosition(tokens[3]);
			if (command1.startsWith("add")) {
				t1 = add(command1, k, lv);
			} else if (command1.startsWith("route")) {
				t3 = route(command1, k);

			}
			String command2 = fineCommandByPosition(tokens[4]);
			if (command2.startsWith("add")) {
				t2 = add(command2, k, lv);
			} else if (command2.startsWith("route")) {
				t4 = route(command2, k);

			}
		}

		z = Math.max(Math.max(t1, t2), Math.max(t3, t4));

		return z;

	}

}
