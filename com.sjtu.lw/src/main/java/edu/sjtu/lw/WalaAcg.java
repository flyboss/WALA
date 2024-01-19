package edu.sjtu.lw;


import com.ibm.wala.cast.js.callgraph.fieldbased.FieldBasedCallGraphBuilder;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraph;
import com.ibm.wala.cast.js.html.DefaultSourceExtractor;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.CallGraph2JSON;
import com.ibm.wala.cast.js.util.FieldBasedCGUtil;
import com.ibm.wala.cast.js.util.FieldBasedCGUtil.BuilderType;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.WalaException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class WalaAcg {

  public static Map<String, String> parseArgs(String[] args) {
    CommandLineParser parser = new DefaultParser();

    Options options = new Options();
    options.addOption("h", "help", false, "显示帮助信息");
    options.addOption("f", "file", true, "the file or project to be analyzed");
    options.addOption("m", "mode", true, "ACG mode: PES or OPT");
    options.addOption("s", "save", true, "the path to save results");

    Map<String, String> dictionary = new HashMap<>();
    try {
      // 解析命令行参数
      CommandLine cmd = parser.parse(options, args);

      // 检查是否包含帮助选项
      if (cmd.hasOption("h")) {
        // 如果包含帮助选项，显示帮助信息
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("myprogram", options); // "myprogram" 是您的程序名称
      } else {
        // 获取文件路径参数值
        String filePath = cmd.getOptionValue("f");
        String mode = cmd.getOptionValue("m");
        String saveDir = cmd.getOptionValue("s");

        dictionary.put("filePath", filePath);
        dictionary.put("mode", mode);
        dictionary.put("saveDir", saveDir);
      }
    } catch (ParseException e) {
      // 解析失败时捕获异常并显示帮助信息
      System.err.println("命令行参数解析失败：" + e.getMessage());
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("myprogram", options); // "myprogram" 是您的程序名称
    }
    return dictionary;
  }

  public static void main(String[] args)
      throws IOException, WalaException, CancelException {
    Map<String, String> argsMap = parseArgs(args);
    Path path = Paths.get(argsMap.get("filePath"));
    String mode = new String(argsMap.get("mode"));
    File dir = new File(argsMap.get("saveDir"));
    if (!dir.exists()){
      dir.mkdir();
    }
    File file1 = new File(argsMap.get("saveDir"),"SCG_"+mode+".json");
    File file2 = new File(argsMap.get("saveDir"),"FG_"+mode+".json");
    FieldBasedCGUtil f = new FieldBasedCGUtil(new CAstRhinoTranslatorFactory());

    FieldBasedCallGraphBuilder.CallGraphResult results = null;
    if(mode.equalsIgnoreCase("PES")){
      results=f.buildScriptDirCG(path, FieldBasedCGUtil.BuilderType.PESSIMISTIC, new NullProgressMonitor(),false);

    }
    else if (mode.equalsIgnoreCase("OPT")){
      results=f.buildScriptDirCG(path, FieldBasedCGUtil.BuilderType.OPTIMISTIC_WORKLIST,new NullProgressMonitor(),false);

    }

    CallGraph CG = results.getCallGraph();
    FlowGraph flowGraph = results.getFlowGraph();


    try {
      FileWriter myWriter1 = new FileWriter(file1);
      myWriter1.write((new CallGraph2JSON(false,true)).serialize(CG));
      myWriter1.close();
      System.out.println("Successfully wrote to Call Graph "+ file1);
    } catch (IOException e) {
      System.out.println("An error occurred while writing Call Graph.");
      e.printStackTrace();
    }
//    try {
//      FileWriter myWriter2 = new FileWriter(file2);
//      myWriter2.write(flowGraph.toJSON());
//      myWriter2.close();
//      System.out.println("Successfully wrote Flow Graph to "+ file2);
//    } catch (IOException e) {
//      System.out.println("An error occurred while writing Flow Graph.");
//      e.printStackTrace();
//    }

  }
}
