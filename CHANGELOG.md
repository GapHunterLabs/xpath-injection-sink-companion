<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# XPath Injection Sink Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.0]

### Added

- Hand-written XPath 1.0 tokenizer + recursive-descent parser (this
  catalog's third full custom grammar).
- Same-method taint detection from an HTTP endpoint parameter to
  `XPath.evaluate(...)`/`.compile(...)` (CWE-643), with the grammar
  used to validate the argument's static skeleton parses as
  well-formed XPath before flagging (noise reduction, never a security
  gate).

[Unreleased]: https://github.com/GapHunterLabs/xpath-injection-sink-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/xpath-injection-sink-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/xpath-injection-sink-companion/commits/0.1.0
