package edu.sjtu.lw;


import com.ibm.wala.cast.js.callgraph.fieldbased.FieldBasedCallGraphBuilder;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraph;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.ObjectVertex;
import com.ibm.wala.cast.js.html.DefaultSourceExtractor;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraph;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.CallGraph2JSON;
import com.ibm.wala.cast.js.util.FieldBasedCGUtil;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) throws MalformedURLException, WalaException, CancelException {
    Path path = Paths.get(args[0]);
    URL url = path.toUri().toURL();
    FieldBasedCGUtil f = new FieldBasedCGUtil(new CAstRhinoTranslatorFactory());
    FieldBasedCallGraphBuilder.CallGraphResult results =
        f.buildCG(url, FieldBasedCGUtil.BuilderType.OPTIMISTIC_WORKLIST, false, DefaultSourceExtractor::new);
    CallGraph CG = results.getCallGraph();
    System.out.println(CallGraphStats.getStats(CG));
    System.out.println("CALL GRAPH:");
    System.out.println((new CallGraph2JSON(false)).serialize(CG));
    FlowGraph flowGraph = results.getFlowGraph();
    System.out.println("FLOW GRAPH:");
    System.out.println(flowGraph.toJSON());
    //System.out.println(CG);
  }
}
