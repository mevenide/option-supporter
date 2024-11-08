package org.mevenide.optionsupporter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
class OptionSupporterProcessorFunctionalTest {

  private final File sourceDir = new File("./src/test/test_src");

  private static JavaToolCompiler javac;
  private static JavaToolCompiler ecj;

  @BeforeAll
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void beforeAll() {
    javac = new JavaToolCompiler(ToolProvider.getSystemJavaCompiler());
    ecj = new JavaToolCompiler(new EclipseCompiler());

    File classDir = new File("./target/test_tmp/classes");
    classDir.mkdirs();

    Stream.of(javac, ecj)
        .forEach(
            cc -> {
              try {
                cc.javaFileManager.setLocation(StandardLocation.SOURCE_PATH, List.of(sourceDir));
                cc.javaFileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classDir));
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
  }

  @AfterAll
  static void afterAll() {
    javac = null;
    ecj = null;
  }

  static List<Arguments> compilers() {
    return List.of(arguments(javac), arguments(ecj));
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void consumesOption(JavaToolCompiler compiler) throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(compiler, "-AoptionSupporter.supportedOptions=my.option", "-Amy.option=true");

    assertThat(errorsAndWarnings, empty());
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void consumesMultipleOptions(JavaToolCompiler compiler) throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler,
            "-AoptionSupporter.supportedOptions=my.option,my.option2",
            "-Amy.option=true",
            "-Amy.option2=false");

    assertThat(errorsAndWarnings, empty());
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void doesNotConsumeUnsupportedOptions(JavaToolCompiler compiler) throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler, "-AoptionSupporter.supportedOptions=my.option", "-Amy.option2=false");

    if (compiler.toString().equals("EclipseCompiler")) {
      // ecj doesn't seem to warn for these
      // TODO possibly some option needs to be set?
      assertThat(errorsAndWarnings, empty());
    } else {
      assertWarnsUnsupportedOptions(errorsAndWarnings, "my.option2");
    }
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void consumesAllOptions(JavaToolCompiler compiler) throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler,
            "-AoptionSupporter.supportedOptions=*",
            "-Amy.option=true",
            "-Amy.option2=false",
            "-Amy.option3=123");

    assertThat(errorsAndWarnings, empty());
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void warnsAboutRedundantOptionsWithWildcard(JavaToolCompiler compiler) throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler,
            "-AoptionSupporter.supportedOptions=*,my.option",
            "-Amy.option=true",
            "-Amy.option2=false",
            "-Amy.option3=123");

    assertThat(errorsAndWarnings, hasSize(1));
    Diagnostic<?> diag = errorsAndWarnings.get(0);
    assertThat(diag.getKind(), is(Kind.WARNING));
    assertThat(
        diag.getMessage(Locale.ROOT),
        containsString(
            "'optionSupporter.supportedOptions' contains wildcard '*', other values are redundant"));
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void processesSupportedAnnotations(JavaToolCompiler compiler) throws IOException {
    // SomeAnnotation appears on Test1
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler,
            "-AoptionSupporter.supportedOptions=my.option",
            "-AoptionSupporter.supportedAnnotationTypes=org.mevenide.optionsupporter.test.SomeAnnotation",
            "-Amy.option=true");

    assertThat(errorsAndWarnings, empty());
  }

  @ParameterizedTest
  @MethodSource("compilers")
  void doesNotProcessUnsupportedAnnotations(JavaToolCompiler compiler) throws IOException {
    // SomeAnnotation2 does not appear on any class
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            compiler,
            "-AoptionSupporter.supportedOptions=my.option",
            "-AoptionSupporter.supportedAnnotationTypes=org.mevenide.optionsupporter.test.SomeAnnotation2",
            "-Amy.option=true");

    if (compiler.toString().equals("EclipseCompiler")) {
      // ecj doesn't seem to warn for these
      // TODO possibly some option needs to be set?
      assertThat(errorsAndWarnings, empty());
    } else {
      assertWarnsUnsupportedOptions(
          errorsAndWarnings,
          "optionSupporter.supportedAnnotationTypes",
          "optionSupporter.supportedOptions",
          "my.option");
    }
  }

  private List<Diagnostic<?>> compileFiles(JavaToolCompiler compiler, String... options)
      throws IOException {
    List<Path> sourceFiles = findSourceFiles();

    final List<Diagnostic<?>> errorsAndWarnings = Collections.synchronizedList(new ArrayList<>());
    boolean success;
    synchronized (this) {
      JavaCompiler.CompilationTask task =
          compiler.compiler.getTask(
              null,
              compiler.javaFileManager,
              diagnostic -> {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR
                    || diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                  errorsAndWarnings.add(diagnostic);
                }
              },
              List.of(options),
              null,
              compiler.javaFileManager.getJavaFileObjects(sourceFiles.toArray(new Path[0])));

      success = task.call();
    }
    if (!success) {
      throw new RuntimeException("Error compiling sources");
    }

    return errorsAndWarnings;
  }

  private List<Path> findSourceFiles() throws IOException {
    List<Path> sourceFiles = new ArrayList<>();
    Files.walkFileTree(
        sourceDir.toPath(),
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (file.getFileName().toString().endsWith(".java")) {
              sourceFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return sourceFiles;
  }

  private void assertWarnsUnsupportedOptions(
      List<Diagnostic<?>> errorsAndWarnings, String... unsupportedOptions) {
    assertThat(errorsAndWarnings, hasSize(1));
    Diagnostic<?> diag = errorsAndWarnings.get(0);
    assertThat(diag.getKind(), is(Kind.WARNING));
    String msg = diag.getMessage(Locale.ROOT);
    assertThat(msg, containsString("The following options were not recognized by any processor:"));
    Stream.of(unsupportedOptions).forEach(opt -> assertThat(msg, containsString(opt)));
  }

  private static class JavaToolCompiler {

    final JavaCompiler compiler;
    final StandardJavaFileManager javaFileManager;

    public JavaToolCompiler(JavaCompiler compiler) {
      this.compiler = compiler;
      javaFileManager = compiler.getStandardFileManager(null, null, null);
    }

    @Override
    public String toString() {
      return compiler.getClass().getSimpleName();
    }
  }
}
