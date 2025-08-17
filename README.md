[![codecov](https://codecov.io/gh/PinkieSwirl/PieDelta/graph/badge.svg?token=NBJBBQGJD8)](https://codecov.io/gh/PinkieSwirl/PieDelta)
![Code QL](https://github.com/PinkieSwirl/PieDelta/actions/workflows/codeql.yml/badge.svg)

# PieDelta

A Kotlin (jvm) implementation of the GDIFF algorithm as specified by the W3C in the
[GDIFF - Generic Diff Format Specification ](https://www.w3.org/TR/NOTE-gdiff-19970901).

The modified Adler-32 rolling hash implementation is inspired from the
[xdelta project](https://sourceforge.net/projects/xdelta).

## Features

- Complete implementation of the GDIFF format specification
- Written in pure Kotlin with no external runtime dependencies
- Thoroughly tested with extensive unit tests

## Usage

PieDelta offers a straightforward API for generating and applying binary diffs:

### Creating a Diff

```kotlin
// Create the diff between source and target paths with default settings
Delta.create(
    source = Path.of("original-folder"),
    target = Path.of("modified-folder"),
    patch = Path.of("changes.zip"),
)
```

### Applying a Diff

```kotlin
// Apply the patch
val zipPatch
Delta.patch(
    zipPatch = ZipInputStream(Path.of("changes.zip").inputStream().buffered()),
    target = Path.of("original-folder"),
)
```