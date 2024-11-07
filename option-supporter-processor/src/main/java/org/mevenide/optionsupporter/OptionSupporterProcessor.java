package org.mevenide.optionsupporter;

import com.google.auto.service.AutoService;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

/**
 * Annotation processor that consumes the options configured by {@link #SUPPORTED_OPTIONS}.
 * Optionally can be configured to only process {@link #SUPPORTED_ANNOTATION_TYPES} (i.e. if those
 * annotations types are not present, it will not be enabled and so will not consume any options).
 */
@AutoService(Processor.class)
@SupportedOptions({
  OptionSupporterProcessor.SUPPORTED_ANNOTATION_TYPES,
  OptionSupporterProcessor.SUPPORTED_OPTIONS,
})
public class OptionSupporterProcessor extends AbstractProcessor {

  /** Comma-separated list of full-qualified types. */
  public static final String SUPPORTED_ANNOTATION_TYPES =
      "optionSupporter.supportedAnnotationTypes";

  /**
   * Comma-separated list of option names. Can also be "*" (and only "*") to consume all options.
   */
  public static final String SUPPORTED_OPTIONS = "optionSupporter.supportedOptions";

  private Set<String> supportedAnnotationTypes;
  private Set<String> supportedOptions;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized Set<String> getSupportedAnnotationTypes() {
    // if not initialized yet, return wildcard
    if (!isInitialized()) {
      return Collections.singleton("*");
    }

    // return types from option, else wildcard
    synchronized (this) {
      if (supportedAnnotationTypes == null) {
        String[] supported = parseSupportedAnnotationTypes();
        if (supported.length == 0) {
          supportedAnnotationTypes = Collections.singleton("*");
        } else {
          // don't bother stripping modules (as e.g. `AbstractProcessor` does)
          supportedAnnotationTypes =
              Collections.unmodifiableSet(new HashSet<>(Arrays.asList(supported)));
        }
      }
    }
    return supportedAnnotationTypes;
  }

  private String[] parseSupportedAnnotationTypes() {
    return splitOption(processingEnv.getOptions().get(SUPPORTED_ANNOTATION_TYPES));
  }

  @Override
  public Set<String> getSupportedOptions() {
    // if not initialized yet, return default implementation
    if (!isInitialized()) {
      return super.getSupportedOptions();
    }

    // return configured options
    synchronized (this) {
      if (supportedOptions == null) {
        Set<String> inner;

        // check for wildcard
        List<String> parsed = Arrays.asList(parseSupportedOptions());
        if (parsed.contains("*")) {
          if (parsed.size() != 1) {
            // warn if options contains redundant values
            processingEnv
                .getMessager()
                .printMessage(
                    Kind.WARNING,
                    "'"
                        + SUPPORTED_OPTIONS
                        + "' contains wildcard '*', other values are redundant");
          }
          // just return all provided options
          inner = new HashSet<>(processingEnv.getOptions().keySet());
        } else {
          inner = new HashSet<>(super.getSupportedOptions());
          inner.addAll(parsed);
        }
        supportedOptions = Collections.unmodifiableSet(inner);
      }
    }
    return supportedOptions;
  }

  private String[] parseSupportedOptions() {
    return splitOption(processingEnv.getOptions().get(SUPPORTED_OPTIONS));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return false;
  }

  private String[] splitOption(String optionStr) {
    // TODO support escaping ','
    if (optionStr == null || optionStr.trim().isEmpty()) {
      return new String[0];
    }
    return optionStr.split(",");
  }
}
