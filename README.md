# option-supporter

This is a simple annotation processor that 'consumes' annotation processor options. It exists to
prevent `warning: The following options were not recognized by any processor:` warnings from the
java compiler. The actual annotation processing step is a no-op.

## Usage

Add `option-supporter-processor` as an annotation processor and tell it which options you'd like
it to consume so that they won't generate warnings. This is an example `maven-compiler-plugin`
configuration:

```xml

<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs>
      <arg>-Adont.warn=about.me</arg>
      <arg>-AoptionSupporter.supportedOptions=dont.warn</arg>
    </compilerArgs>
    <annotationProcessorPaths>
      ...
      <path>
        <groupId>org.mevenide.optionsupporter</groupId>
        <artifactId>option-supporter-processor</artifactId>
        <version>0.1.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

### Configuration

It only supports two options:

* `optionSupporter.supportedOptions`: a comma-separated list of annotation processor options that it
  should consume, e.g. `my.option1,my.option2`. At the moment if your option name contains `,` then
  you're out of luck. You can specify a single wildcard value `*`, in which case the processor will
  consume all annotation processor options (i.e. warnings will not be generated for any unrecognized
  options).
* `optionSupporter.supportedAnnotationTypes`: a comma-separated list of annotation types that
  restricts whether the processor is activated or not, e.g.
  `my.custom.Annotation1,my.custom.Annotation2`. If none of the listed types are present then
  the annotation processor will not be active, and so it won't consume any of the configured
  annotation processor options. The default value is the wildcard `*`, i.e. the processor is always
  active.
