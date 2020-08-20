package cn.edu.xmut.main;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.StringRegexExpression;

import scala.Tuple2;

public class Swat {

	public static <U> void main(String[] args) {
		String path;
		int depth;
		int simulation;
		int executors;
		if (args.length < 4) {
			System.out.println(
					"Arguments required: nfs path, depth, simulation number, executor number");
			return;
		} else {
			path = args[0];
			depth = Integer.parseInt(args[1]);
			simulation = Integer.parseInt(args[2]);
			executors = Integer.parseInt(args[3]);
		}

		Map<String, String> lookuptableMap = new HashMap<String, String>();
		FileReader fig_filereader;
		File fig_file = new File(path + "/fig.fig");
		try {
			fig_filereader = new FileReader(fig_file);
			List<String> configs = IOUtils.readLines(fig_filereader);

			for (int i = 0; i < configs.size(); i++) {
				if (configs.get(i).startsWith("subbasin")) {
					String line = configs.get(i);
					String ww[] = line.split("\\s+");
					String line2 = configs.get(i + 1);
					String old = line2.substring(line2.indexOf("0"),
							8 + line2.indexOf("0") + 1);
					//将新的原号与新号存入到查找字典当中
					lookuptableMap.put(ww[3], old);

				}
			}
			System.out.println(lookuptableMap.toString());

		} catch (IOException e1) {
			e1.printStackTrace();
		}

		Map<String, ArrayList<String>> uslookuptableMap = new HashMap<String, ArrayList<String>>();
		try {
			FileReader filereader;
			File file = new File(path + "/routeset.txt");
			filereader = new FileReader(file);
			List<String> lines = IOUtils.readLines(filereader);
			for (int i = 0; i < lines.size(); i++) {
				String[] tokens = lines.get(i).split(",");
				String key = tokens[1];
				String value = tokens[0];
				if (!key.equals("-1")) {
					if (uslookuptableMap.containsKey(key)) {
						ArrayList list = uslookuptableMap.get(key);
						list.add(value);
					} else {
						ArrayList list = new ArrayList<String>();
						list.add(value);
						uslookuptableMap.put(key, list);
					}
				}

			}

			for (int i = 0; i < lines.size(); i++) {
				String[] tokens = lines.get(i).split(",");
				String key = tokens[0];
				if (!uslookuptableMap.containsKey(key)) {
					uslookuptableMap.put(key, null);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ArrayList<String> arrayList = new ArrayList<String>();
		for (int z = 1; z <= simulation; z++) {
			arrayList.add(z + "");
		}

		for (int i = 1; i <= depth; i++) {
			SparkSession spark = SparkSession.builder()
					.appName("swat simulation").getOrCreate();
			JavaSparkContext jsc = new JavaSparkContext(spark.sparkContext());
			String modelpath = "/home/application/models/";
			final Broadcast<Map<String, String>> blookuptable = jsc
					.broadcast(lookuptableMap);
			final Broadcast<Map<String, ArrayList<String>>> buslookuptableMap = jsc
					.broadcast(uslookuptableMap);
			final Broadcast<String> bmodelpath = jsc.broadcast(modelpath);
			final Broadcast<String> bnfspath = jsc.broadcast(path);
			final Broadcast<Integer> bworknode_cores = jsc.broadcast(executors);
			JavaRDD<String> simRDD = jsc.parallelize(arrayList);
			JavaRDD<String> route = jsc.textFile(path + "/routeset.txt");
			//将路由信息与模拟次数联系在一起，最终形成"模拟号_子流域编号,模拟号_下流子流域编号,路由次序"的数据集
			JavaRDD<String> froute = simRDD.cartesian(route)
					.map(new Function<Tuple2<String, String>, String>() {
						@Override
						public String call(Tuple2<String, String> input)
								throws Exception {
							UtilsFunctions.println("first:" + input._1());
							UtilsFunctions.println("second" + input._2());
							String[] sps = input._2().split(",");
							String rs = input._1() + "_" + sps[0] + ","
									+ input._1() + "_" + sps[1] + "," + sps[2];
							return rs;
						}
					});

			//选出（过滤）指定次序的路由数据集
			JavaRDD<String> sub = froute.filter(new MyFilterFunction(i));
			//针对每个子流域，执行子流域模拟
			List<String> finalrs = sub.map(new Function<String, String>() {
				@Override
				public String call(String v1) throws Exception {
					UtilsFunctions.println("xxxxxxxxxxxxxxxxxxxxxx");
					String[] tokens = v1.split(",");
					String[] sim_csub = tokens[0].split("_");
					String sim = sim_csub[0];
					sim = UtilsFunctions.pad("0", Integer.parseInt(sim), 4);
					String csub = sim_csub[1];

					String sub = UtilsFunctions.pad("0", Integer.parseInt(csub),
							5);

					int availablemodel = UtilsFunctions
							.getFreeModelIndex(bworknode_cores.getValue());
					String modelpath = bmodelpath.getValue() + "model"
							+ availablemodel + "/";
					String nfspath = bnfspath.getValue();
					ArrayList<String> upsubs = buslookuptableMap.getValue()
							.get(csub);
					String upsubs_string = "";
					if (upsubs != null) {
						for (int j = 0; j < upsubs.size(); j++) {
							upsubs_string = upsubs_string
									+ UtilsFunctions.pad("0",
											Integer.parseInt(upsubs.get(j)), 5)
									+ " ";
						}
					}
					upsubs_string = upsubs_string.trim();
					String command = modelpath + "invokemodel.sh " + nfspath
							+ " " + modelpath + " " + sub + " " + sim + " "
							+ upsubs_string;
					UtilsFunctions.exeComand(command);
					UtilsFunctions.println(command);
					UtilsFunctions.println("-------------------------------");
					return v1;
				}
			}).collect();

			for (int k = 0; k < finalrs.size(); k++) {
				System.out.println("------------------------" + finalrs.get(k));
			}
			spark.stop();
		}

	}

	static class MyFilterFunction implements Function<String, Boolean> {

		int depth;

		public MyFilterFunction(int dth) {
			depth = dth;
		}

		@Override
		public Boolean call(String v1) throws Exception {
			String[] tokens = v1.split(",");
			if (tokens[2].equals(depth + "")) {
				return true;
			} else {
				return false;
			}
		}

	}

}
