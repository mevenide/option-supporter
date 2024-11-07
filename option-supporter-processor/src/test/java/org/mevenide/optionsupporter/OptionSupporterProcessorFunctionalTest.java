package org.mevenide.optionsupporter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
class OptionSupporterProcessorFunctionalTest {

  private JavaCompiler compiler;
  private StandardJavaFileManager javaFileManager;
  private final File sourceDir = new File("./src/test/test_src");

  @BeforeAll
  @SuppressWarnings("ResultOfMethodCallIgnored")
  void beforeAll() throws IOException {
    compiler = ToolProvider.getSystemJavaCompiler();
    javaFileManager = compiler.getStandardFileManager(null, null, null);

    File classDir = new File("./target/test_tmp/classes");
    classDir.mkdirs();

    javaFileManager.setLocation(StandardLocation.SOURCE_PATH, List.of(sourceDir));
    javaFileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classDir));
  }

  @Test
  void consumesOption() throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles("-AoptionSupporter.supportedOptions=my.option", "-Amy.option=true");

    assertThat(errorsAndWarnings, Matchers.empty());
  }

  @Test
  void consumesMultipleOptions() throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            "-AoptionSupporter.supportedOptions=my.option,my.option2",
            "-Amy.option=true",
            "-Amy.option2=false");

    assertThat(errorsAndWarnings, Matchers.empty());
  }

  @Test
  void doesNotConsumeUnsupportedOptions() throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles("-AoptionSupporter.supportedOptions=my.option", "-Amy.option2=false");

    assertWarnsUnsupportedOptions(errorsAndWarnings, "my.option2");
  }

  @Test
  void consumesAllOptions() throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            "-AoptionSupporter.supportedOptions=*",
            "-Amy.option=true",
            "-Amy.option2=false",
            "-Amy.option3=123");

    assertThat(errorsAndWarnings, Matchers.empty());
  }

  @Test
  void warnsAboutRedundantOptionsWithWildcard() throws IOException {
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
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

  @Test
  void processesSupportedAnnotations() throws IOException {
    // SomeAnnotation appears on Test1
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            "-AoptionSupporter.supportedOptions=my.option",
            "-AoptionSupporter.supportedAnnotationTypes=org.mevenide.optionsupporter.test.SomeAnnotation",
            "-Amy.option=true");

    assertThat(errorsAndWarnings, Matchers.empty());
  }

  @Test
  void doesNotProcessUnsupportedAnnotations() throws IOException {
    // SomeAnnotation2 does not appear on any class
    final List<Diagnostic<?>> errorsAndWarnings =
        compileFiles(
            "-AoptionSupporter.supportedOptions=my.option",
            "-AoptionSupporter.supportedAnnotationTypes=org.mevenide.optionsupporter.test.SomeAnnotation2",
            "-Amy.option=true");

    assertWarnsUnsupportedOptions(
        errorsAndWarnings,
        "optionSupporter.supportedAnnotationTypes",
        "optionSupporter.supportedOptions",
        "my.option");
  }

  private List<Diagnostic<?>> compileFiles(String... options) throws IOException {
    List<Path> sourceFiles = findSourceFiles();

    final List<Diagnostic<?>> errorsAndWarnings = Collections.synchronizedList(new ArrayList<>());
    boolean success;
    synchronized (this) {
      JavaCompiler.CompilationTask task =
          compiler.getTask(
              null,
              javaFileManager,
              diagnostic -> {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR
                    || diagnostic.getKind() == Diagnostic.Kind.WARNING) {
                  errorsAndWarnings.add(diagnostic);
                }
              },
              List.of(options),
              null,
              javaFileManager.getJavaFileObjects(sourceFiles.toArray(new Path[0])));

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
}
