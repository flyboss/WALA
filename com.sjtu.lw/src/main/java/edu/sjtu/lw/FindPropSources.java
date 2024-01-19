package edu.sjtu.lw;

import com.ibm.wala.cast.ipa.callgraph.CAstAnalysisScope;
import com.ibm.wala.cast.ir.ssa.AstGlobalRead;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.ir.ssa.AstLexicalRead;
import com.ibm.wala.cast.ir.ssa.EachElementGetInstruction;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.loader.JavaScriptLoaderFactory;
import com.ibm.wala.cast.js.ssa.JavaScriptInvoke;
import com.ibm.wala.cast.js.ssa.JavaScriptPropertyRead;
import com.ibm.wala.cast.js.ssa.JavaScriptPropertyWrite;
import com.ibm.wala.cast.js.ssa.JavaScriptTypeOfInstruction;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.SourceURLModule;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ReflectiveMemberAccess;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.collections.HashSetFactory;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import java.io.File; 
import java.io.FileWriter;

import java.lang.reflect.Field;

public class FindPropSources {

  /**
   * Usage: FindPropSources source_dir. Dumps information on all dynamic property accesses
   * encountered
   *
   * @param args
   */
  public static void main(String[] args) throws IOException, ClassHierarchyException {
    Path scriptDir = Paths.get(args[0]);
    File dir = new File(args[1]);
    File file1 = new File(args[1]+"/StaticProps"+".json");
    IClassHierarchy cha = getCHAForScripts(scriptDir);
    IAnalysisCacheView cache = new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory());
    List<AccessAndSources> result = new ArrayList<>();
    for (IClass klass : cha) {
      for (IMethod method : klass.getDeclaredMethods()) {
        if (method.getDescriptor().equals(AstMethodReference.fnDesc)
            && method instanceof AstMethod) {
          IR ir = cache.getIR(method);
          //          System.err.println(ir);
          DefUse du = new DefUse(ir);
          SSAInstruction[] instructions = ir.getInstructions();
          for (int ind = 0; ind < instructions.length; ind++) {
            SSAInstruction inst = instructions[ind];
            if (inst == null) {
              continue;
            }
            CAstSourcePositionMap.Position sourcePosition =
                ((AstMethod) method).getSourcePosition(inst.iIndex());
            if (inst instanceof ReflectiveMemberAccess) {
              ReflectiveMemberAccess dynAccess = (ReflectiveMemberAccess) inst;
              //              System.err.println("dynamic access " + dynAccess);
              //              System.err.println(
              //                  sourcePosition.prettyPrint());
              Set<IRSource> sources = HashSetFactory.make();
              addAllDefs(ir, du, dynAccess.getMemberRef(), sources, HashSetFactory.make());
              //              System.err.println("sources: " + sources);
              result.add(createAccessAndSources((AstMethod) method, ir, dynAccess, sources));
            } else if (inst instanceof JavaScriptInvoke) {
              // this has a property access built-in!
              JavaScriptInvoke invoke = (JavaScriptInvoke) inst;
              //              System.err.println("dynamic access in invoke " + invoke);
              //              System.err.println(
              //                  sourcePosition.prettyPrint());
              Set<IRSource> sources = HashSetFactory.make();
              addAllDefs(ir, du, invoke.getFunction(), sources, HashSetFactory.make());
              //              System.err.println("sources: " + sources);
              result.add(createAccessAndSources((AstMethod) method, ir, invoke, sources));
            }
          }
        }
      }
    }
    Type listOfAccessAndSources = Types.newParameterizedType(List.class, AccessAndSources.class);
    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<List<AccessAndSources>> adapter = moshi.adapter(listOfAccessAndSources);
    String finalJson = adapter.indent("  ").toJson(result);
    try {  
      FileWriter myWriter1 = new FileWriter(file1);
      myWriter1.write(finalJson);
      myWriter1.close();
      System.out.println("Successfully wrote to Props "+ file1);
    } catch (IOException e) {
      System.out.println("An error occurred while writing Props.");
      e.printStackTrace();
    }
  }

  private static AccessAndSources createAccessAndSources(
      AstMethod method, IR ir, SSAInstruction dynAccess, Set<IRSource> sources) {
    boolean isPut = dynAccess instanceof JavaScriptPropertyWrite;

    String accessLoc = method.getSourcePosition(dynAccess.iIndex()).prettyPrint();
    Set<Explanation> explanations = HashSetFactory.make();
    for (IRSource irSource : sources) {
      Explanation explanation;
      if (irSource instanceof ConstSource) {
        explanation =
            new Explanation("CONSTANT", Objects.toString(((ConstSource) irSource).constValue));
      } else if (irSource instanceof ParamSource) {
        ParamSource paramSource = (ParamSource) irSource;
        CAstSourcePositionMap.Position parameterPosition =
            method.getParameterPosition(paramSource.paramValNum - 1);
        explanation =
            new Explanation(
                "PARAM", parameterPosition == null ? "null" : parameterPosition.prettyPrint());
      } else {
        InstSource instSource = (InstSource) irSource;
        SSAInstruction instr = instSource.inst;
        String instrLoc = method.getSourcePosition(instr.iIndex()).prettyPrint();
        String tag;
        if (instr instanceof SSABinaryOpInstruction) {
          tag = "BINARY";
          SymbolTable symbolTable = ir.getSymbolTable();
          if ( symbolTable.getValue(instr.getUse(0)) != null && symbolTable.getValue(instr.getUse(0)).isStringConstant() ){
            tag+="PREFIX";
          }
          if ( symbolTable.getValue(instr.getUse(1)) != null && symbolTable.getValue(instr.getUse(1)).isStringConstant() ){
            tag+="POSTFIX";
          }
        } else if (instr instanceof SSAUnaryOpInstruction) {
          tag = "UNARY";
        } else if (instr instanceof JavaScriptTypeOfInstruction) {
          tag = "TYPEOF";
        } else if (instr instanceof AstLexicalRead) {
          tag = "LEXICAL_READ";
        } else if (instr instanceof AstGlobalRead) {
          tag = "GLOBAL_READ";
        } else if (instr instanceof SSAGetInstruction || instr instanceof JavaScriptPropertyRead) {
          tag = "GET";
        } else if (instr instanceof EachElementGetInstruction) {
          tag = "FORINVAR";
        } else if (instr instanceof SSAAbstractInvokeInstruction) {
          tag = "FUNCTION_RETURN";
        } else {
          tag = "OTHER";
        }
        explanation = new Explanation(tag, instrLoc);
      }
      explanations.add(explanation);
    }
    return new AccessAndSources(isPut, accessLoc, explanations);
  }

  private static class Explanation {
    private final String tag;
    private final String info;

    public Explanation(String tag, String info) {
      this.tag = tag;
      this.info = info;
    }

    public String getTag() {
      return tag;
    }

    public String getInfo() {
      return info;
    }
  }

  private static class AccessAndSources {
    private final boolean isPut;
    private final String accessLoc;
    private final Set<Explanation> explanations;

    public AccessAndSources(boolean isPut, String accessLoc, Set<Explanation> explanations) {
      this.isPut = isPut;
      this.accessLoc = accessLoc;
      this.explanations = explanations;
    }

    public boolean isPut() {
      return isPut;
    }

    public String getAccessLoc() {
      return accessLoc;
    }

    public Set<Explanation> getExplanations() {
      return explanations;
    }

    @Override
    public String toString() {
      return "AccessAndSources{"
          + "isPut="
          + isPut
          + ", accessLoc='"
          + accessLoc
          + '\''
          + ", explanations="
          + explanations
          + '}';
    }
  }

  private static IClassHierarchy getCHAForScripts(Path scriptDir)
      throws IOException, ClassHierarchyException {
    List<Path> jsFiles =
        Files.walk(scriptDir)
            .filter((fn) -> fn.toString().toLowerCase().endsWith(".js"))
            .collect(Collectors.toList());
    List<Module> scripts = new ArrayList<>();
    for (Path jsFile : jsFiles) {
      scripts.add(new SourceURLModule(jsFile.toUri().toURL()));
    }
    scripts.add(JSCallGraphUtil.getPrologueFile("prologue.js"));
    JavaScriptLoaderFactory loaders = new JavaScriptLoaderFactory(new CAstRhinoTranslatorFactory());
    CAstAnalysisScope scope =
        new CAstAnalysisScope(
            scripts.toArray(new Module[0]), loaders, Collections.singleton(JavaScriptLoader.JS));
    return ClassHierarchyFactory.make(scope, loaders, JavaScriptLoader.JS);
  }

  private interface IRSource {}

  private static class InstSource implements IRSource {
    private final SSAInstruction inst;

    private InstSource(SSAInstruction inst) {
      this.inst = inst;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      InstSource that = (InstSource) o;
      return inst.equals(that.inst);
    }

    @Override
    public int hashCode() {
      return Objects.hash(inst);
    }

    @Override
    public String toString() {
      return "InstSource{" + "inst=" + inst + '}';
    }
  }

  private static class ParamSource implements IRSource {
    private final int paramValNum;

    private ParamSource(int paramValNum) {
      this.paramValNum = paramValNum;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ParamSource that = (ParamSource) o;
      return paramValNum == that.paramValNum;
    }

    @Override
    public int hashCode() {
      return Objects.hash(paramValNum);
    }

    @Override
    public String toString() {
      return "ParamSource{" + "paramValNum=" + paramValNum + '}';
    }
  }

  private static class ConstSource implements IRSource {
    private final Object constValue;

    private ConstSource(Object constValue) {
      this.constValue = constValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ConstSource that = (ConstSource) o;
      return Objects.equals(constValue, that.constValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(constValue);
    }

    @Override
    public String toString() {
      return "ConstSource{" + "constValue=" + constValue + '}';
    }
  }

  private static void addAllDefs(
      IR ir, DefUse du, int valNum, Set<IRSource> result, Set<Integer> queried) {
    queried.add(valNum);
    SSAInstruction def = du.getDef(valNum);
    if (def != null) {
      if (def instanceof SSAPhiInstruction) {
        // recurse to uses
        int numberOfUses = def.getNumberOfUses();
        for (int i = 0; i < numberOfUses; i++) {
          int use = def.getUse(i);
          if (use != -1 && !queried.contains(use)) {
            addAllDefs(ir, du, use, result, queried);
          }
        }
      } else {
        result.add(new InstSource(def));
      }
    } else {
      // should be a parameter or a constant
      if (valNum <= ir.getNumberOfParameters()) {
        result.add(new ParamSource(valNum));
      } else {
        SymbolTable symbolTable = ir.getSymbolTable();
        Object constantValue = symbolTable.getConstantValue(valNum);
        result.add(new ConstSource(constantValue));
      }
    }
  }

  static String readFile(Path path) throws IOException {
    byte[] encoded = Files.readAllBytes(path);
    return new String(encoded, Charset.defaultCharset());
  }
}
