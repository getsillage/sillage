# Third-party notices

Sillage is licensed under the [MIT License](LICENSE). The official server binary and container image also include the Go and Web runtime dependencies listed below. Each dependency remains subject to its own license; the exact license and notice files shipped by that dependency are preserved under `third_party/licenses/` and copied into the official container image at `/usr/share/licenses/sillage/`. The Android APK has a separate locked runtime graph and ships its generated inventory and license texts in Settings; the reviewed Android source texts are preserved under `third_party/licenses/android/`.

This inventory is generated from the resolved Go build graph and the production pnpm dependency graph. Regenerate it with `node scripts/generate-third-party-notices.mjs --write`; CI rejects dependency or notice drift.

## Go runtime dependencies

| Module | Version | Declared license | Preserved files |
| --- | --- | --- | --- |
| connectrpc.com/connect | v1.20.0 | Apache-2.0 | [LICENSE](third_party/licenses/go/connectrpc.com/connect@v1.20.0/LICENSE) |
| github.com/dustin/go-humanize | v1.0.1 | MIT | [LICENSE](third_party/licenses/go/github.com/dustin/go-humanize@v1.0.1/LICENSE) |
| github.com/fsnotify/fsnotify | v1.9.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/github.com/fsnotify/fsnotify@v1.9.0/LICENSE) |
| github.com/go-viper/mapstructure/v2 | v2.4.0 | MIT | [LICENSE](third_party/licenses/go/github.com/go-viper/mapstructure/v2@v2.4.0/LICENSE) |
| github.com/google/uuid | v1.6.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/github.com/google/uuid@v1.6.0/LICENSE) |
| github.com/grpc-ecosystem/grpc-gateway/v2 | v2.29.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/github.com/grpc-ecosystem/grpc-gateway/v2@v2.29.0/LICENSE) |
| github.com/labstack/echo/v5 | v5.2.1 | MIT | [LICENSE](third_party/licenses/go/github.com/labstack/echo/v5@v5.2.1/LICENSE) |
| github.com/pelletier/go-toml/v2 | v2.2.4 | MIT | [LICENSE](third_party/licenses/go/github.com/pelletier/go-toml/v2@v2.2.4/LICENSE) |
| github.com/remyoudompheng/bigfft | v0.0.0-20230129092748-24d4a6f8daec | BSD-3-Clause | [LICENSE](third_party/licenses/go/github.com/remyoudompheng/bigfft@v0.0.0-20230129092748-24d4a6f8daec/LICENSE) |
| github.com/sagikazarmark/locafero | v0.11.0 | MIT | [LICENSE](third_party/licenses/go/github.com/sagikazarmark/locafero@v0.11.0/LICENSE) |
| github.com/sourcegraph/conc | v0.3.1-0.20240121214520-5f936abd7ae8 | MIT | [LICENSE](third_party/licenses/go/github.com/sourcegraph/conc@v0.3.1-0.20240121214520-5f936abd7ae8/LICENSE) |
| github.com/spf13/afero | v1.15.0 | Apache-2.0 | [LICENSE.txt](third_party/licenses/go/github.com/spf13/afero@v1.15.0/LICENSE.txt) |
| github.com/spf13/cast | v1.10.0 | MIT | [LICENSE](third_party/licenses/go/github.com/spf13/cast@v1.10.0/LICENSE) |
| github.com/spf13/cobra | v1.10.2 | Apache-2.0 | [LICENSE.txt](third_party/licenses/go/github.com/spf13/cobra@v1.10.2/LICENSE.txt) |
| github.com/spf13/pflag | v1.0.10 | BSD-3-Clause | [LICENSE](third_party/licenses/go/github.com/spf13/pflag@v1.0.10/LICENSE) |
| github.com/spf13/viper | v1.21.0 | MIT | [LICENSE](third_party/licenses/go/github.com/spf13/viper@v1.21.0/LICENSE) |
| github.com/subosito/gotenv | v1.6.0 | MIT | [LICENSE](third_party/licenses/go/github.com/subosito/gotenv@v1.6.0/LICENSE) |
| go.yaml.in/yaml/v3 | v3.0.4 | MIT | [LICENSE](third_party/licenses/go/go.yaml.in/yaml/v3@v3.0.4/LICENSE), [NOTICE](third_party/licenses/go/go.yaml.in/yaml/v3@v3.0.4/NOTICE) |
| golang.org/x/crypto | v0.53.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/golang.org/x/crypto@v0.53.0/LICENSE) |
| golang.org/x/net | v0.56.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/golang.org/x/net@v0.56.0/LICENSE) |
| golang.org/x/sys | v0.46.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/golang.org/x/sys@v0.46.0/LICENSE) |
| golang.org/x/text | v0.39.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/golang.org/x/text@v0.39.0/LICENSE) |
| golang.org/x/time | v0.14.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/golang.org/x/time@v0.14.0/LICENSE) |
| google.golang.org/genproto/googleapis/api | v0.0.0-20260622175928-b703f567277d | Apache-2.0 | [LICENSE](third_party/licenses/go/google.golang.org/genproto/googleapis/api@v0.0.0-20260622175928-b703f567277d/LICENSE) |
| google.golang.org/genproto/googleapis/rpc | v0.0.0-20260618152121-87f3d3e198d3 | Apache-2.0 | [LICENSE](third_party/licenses/go/google.golang.org/genproto/googleapis/rpc@v0.0.0-20260618152121-87f3d3e198d3/LICENSE) |
| google.golang.org/grpc | v1.82.1 | Apache-2.0 | [LICENSE](third_party/licenses/go/google.golang.org/grpc@v1.82.1/LICENSE), [NOTICE.txt](third_party/licenses/go/google.golang.org/grpc@v1.82.1/NOTICE.txt) |
| google.golang.org/protobuf | v1.36.11 | BSD-3-Clause | [LICENSE](third_party/licenses/go/google.golang.org/protobuf@v1.36.11/LICENSE) |
| modernc.org/libc | v1.74.1 | BSD-3-Clause AND LicenseRef-modernc-libc-third-party | [LICENSE](third_party/licenses/go/modernc.org/libc@v1.74.1/LICENSE), [LICENSE-3RD-PARTY.md](third_party/licenses/go/modernc.org/libc@v1.74.1/LICENSE-3RD-PARTY.md) |
| modernc.org/mathutil | v1.7.1 | BSD-3-Clause | [LICENSE](third_party/licenses/go/modernc.org/mathutil@v1.7.1/LICENSE) |
| modernc.org/memory | v1.11.0 | BSD-3-Clause | [LICENSE](third_party/licenses/go/modernc.org/memory@v1.11.0/LICENSE), [LICENSE-GO](third_party/licenses/go/modernc.org/memory@v1.11.0/LICENSE-GO), [LICENSE-LOGO](third_party/licenses/go/modernc.org/memory@v1.11.0/LICENSE-LOGO), [LICENSE-MMAP-GO](third_party/licenses/go/modernc.org/memory@v1.11.0/LICENSE-MMAP-GO) |
| modernc.org/sqlite | v1.55.0 | BSD-3-Clause AND LicenseRef-SQLite-Public-Domain | [LICENSE](third_party/licenses/go/modernc.org/sqlite@v1.55.0/LICENSE), [SQLITE-LICENSE](third_party/licenses/go/modernc.org/sqlite@v1.55.0/SQLITE-LICENSE) |

## Web runtime dependencies

| Package | Version | Declared license | Preserved files |
| --- | --- | --- | --- |
| [@types/debug](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/debug) | 4.1.13 | MIT | [LICENSE](third_party/licenses/web/@types/debug@4.1.13/LICENSE) |
| [@types/estree-jsx](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/estree-jsx) | 1.0.5 | MIT | [LICENSE](third_party/licenses/web/@types/estree-jsx@1.0.5/LICENSE) |
| [@types/estree](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/estree) | 1.0.9 | MIT | [LICENSE](third_party/licenses/web/@types/estree@1.0.9/LICENSE) |
| [@types/hast](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/hast) | 3.0.4 | MIT | [LICENSE](third_party/licenses/web/@types/hast@3.0.4/LICENSE) |
| [@types/mdast](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/mdast) | 4.0.4 | MIT | [LICENSE](third_party/licenses/web/@types/mdast@4.0.4/LICENSE) |
| [@types/ms](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/ms) | 2.1.0 | MIT | [LICENSE](third_party/licenses/web/@types/ms@2.1.0/LICENSE) |
| [@types/react](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/react) | 19.2.17 | MIT | [LICENSE](third_party/licenses/web/@types/react@19.2.17/LICENSE) |
| [@types/unist](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/unist) | 2.0.11 | MIT | [LICENSE](third_party/licenses/web/@types/unist@2.0.11/LICENSE) |
| [@types/unist](https://github.com/DefinitelyTyped/DefinitelyTyped/tree/master/types/unist) | 3.0.3 | MIT | [LICENSE](third_party/licenses/web/@types/unist@3.0.3/LICENSE) |
| [@ungap/structured-clone](https://github.com/ungap/structured-clone#readme) | 1.3.2 | ISC | [LICENSE](third_party/licenses/web/@ungap/structured-clone@1.3.2/LICENSE) |
| [bail](https://github.com/wooorm/bail#readme) | 2.0.2 | MIT | [license](third_party/licenses/web/bail@2.0.2/license) |
| [ccount](https://github.com/wooorm/ccount#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/ccount@2.0.1/license) |
| [character-entities-html4](https://github.com/wooorm/character-entities-html4#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/character-entities-html4@2.1.0/license) |
| [character-entities-legacy](https://github.com/wooorm/character-entities-legacy#readme) | 3.0.0 | MIT | [license](third_party/licenses/web/character-entities-legacy@3.0.0/license) |
| [character-entities](https://github.com/wooorm/character-entities#readme) | 2.0.2 | MIT | [license](third_party/licenses/web/character-entities@2.0.2/license) |
| [character-reference-invalid](https://github.com/wooorm/character-reference-invalid#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/character-reference-invalid@2.0.1/license) |
| [comma-separated-tokens](https://github.com/wooorm/comma-separated-tokens#readme) | 2.0.3 | MIT | [license](third_party/licenses/web/comma-separated-tokens@2.0.3/license) |
| [cookie-es](https://github.com/unjs/cookie-es#readme) | 3.1.1 | MIT | [LICENSE](third_party/licenses/web/cookie-es@3.1.1/LICENSE) |
| [csstype](https://github.com/frenic/csstype#readme) | 3.2.3 | MIT | [LICENSE](third_party/licenses/web/csstype@3.2.3/LICENSE) |
| [debug](https://github.com/debug-js/debug#readme) | 4.4.3 | MIT | [LICENSE](third_party/licenses/web/debug@4.4.3/LICENSE) |
| [decode-named-character-reference](https://github.com/wooorm/decode-named-character-reference#readme) | 1.3.0 | MIT | [license](third_party/licenses/web/decode-named-character-reference@1.3.0/license) |
| [dequal](https://github.com/lukeed/dequal#readme) | 2.0.3 | MIT | [license](third_party/licenses/web/dequal@2.0.3/license) |
| [devlop](https://github.com/wooorm/devlop#readme) | 1.1.0 | MIT | [license](third_party/licenses/web/devlop@1.1.0/license) |
| [escape-string-regexp](https://github.com/sindresorhus/escape-string-regexp#readme) | 5.0.0 | MIT | [license](third_party/licenses/web/escape-string-regexp@5.0.0/license) |
| [estree-util-is-identifier-name](https://github.com/syntax-tree/estree-util-is-identifier-name#readme) | 3.0.0 | MIT | [license](third_party/licenses/web/estree-util-is-identifier-name@3.0.0/license) |
| [extend](https://github.com/justmoon/node-extend#readme) | 3.0.2 | MIT | [LICENSE](third_party/licenses/web/extend@3.0.2/LICENSE) |
| [hast-util-to-jsx-runtime](https://github.com/syntax-tree/hast-util-to-jsx-runtime#readme) | 2.3.6 | MIT | [license](third_party/licenses/web/hast-util-to-jsx-runtime@2.3.6/license) |
| [hast-util-whitespace](https://github.com/syntax-tree/hast-util-whitespace#readme) | 3.0.0 | MIT | [license](third_party/licenses/web/hast-util-whitespace@3.0.0/license) |
| [html-url-attributes](https://github.com/rehypejs/rehype-minify/tree/main#readme) | 3.0.1 | MIT | [license](third_party/licenses/web/html-url-attributes@3.0.1/license) |
| [inline-style-parser](https://github.com/remarkablemark/inline-style-parser#readme) | 0.2.7 | MIT | [LICENSE](third_party/licenses/web/inline-style-parser@0.2.7/LICENSE) |
| [is-alphabetical](https://github.com/wooorm/is-alphabetical#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/is-alphabetical@2.0.1/license) |
| [is-alphanumerical](https://github.com/wooorm/is-alphanumerical#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/is-alphanumerical@2.0.1/license) |
| [is-decimal](https://github.com/wooorm/is-decimal#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/is-decimal@2.0.1/license) |
| [is-hexadecimal](https://github.com/wooorm/is-hexadecimal#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/is-hexadecimal@2.0.1/license) |
| [is-plain-obj](https://github.com/sindresorhus/is-plain-obj#readme) | 4.1.0 | MIT | [license](third_party/licenses/web/is-plain-obj@4.1.0/license) |
| [longest-streak](https://github.com/wooorm/longest-streak#readme) | 3.1.0 | MIT | [license](third_party/licenses/web/longest-streak@3.1.0/license) |
| [lucide-react](https://lucide.dev) | 1.27.0 | ISC | [LICENSE](third_party/licenses/web/lucide-react@1.27.0/LICENSE) |
| [markdown-table](https://github.com/wooorm/markdown-table#readme) | 3.0.4 | MIT | [license](third_party/licenses/web/markdown-table@3.0.4/license) |
| [mdast-util-find-and-replace](https://github.com/syntax-tree/mdast-util-find-and-replace#readme) | 3.0.2 | MIT | [license](third_party/licenses/web/mdast-util-find-and-replace@3.0.2/license) |
| [mdast-util-from-markdown](https://github.com/syntax-tree/mdast-util-from-markdown#readme) | 2.0.3 | MIT | [license](third_party/licenses/web/mdast-util-from-markdown@2.0.3/license) |
| [mdast-util-gfm-autolink-literal](https://github.com/syntax-tree/mdast-util-gfm-autolink-literal#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/mdast-util-gfm-autolink-literal@2.0.1/license) |
| [mdast-util-gfm-footnote](https://github.com/syntax-tree/mdast-util-gfm-footnote#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/mdast-util-gfm-footnote@2.1.0/license) |
| [mdast-util-gfm-strikethrough](https://github.com/syntax-tree/mdast-util-gfm-strikethrough#readme) | 2.0.0 | MIT | [license](third_party/licenses/web/mdast-util-gfm-strikethrough@2.0.0/license) |
| [mdast-util-gfm-table](https://github.com/syntax-tree/mdast-util-gfm-table#readme) | 2.0.0 | MIT | [license](third_party/licenses/web/mdast-util-gfm-table@2.0.0/license) |
| [mdast-util-gfm-task-list-item](https://github.com/syntax-tree/mdast-util-gfm-task-list-item#readme) | 2.0.0 | MIT | [license](third_party/licenses/web/mdast-util-gfm-task-list-item@2.0.0/license) |
| [mdast-util-gfm](https://github.com/syntax-tree/mdast-util-gfm#readme) | 3.1.0 | MIT | [license](third_party/licenses/web/mdast-util-gfm@3.1.0/license) |
| [mdast-util-mdx-expression](https://github.com/syntax-tree/mdast-util-mdx-expression#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/mdast-util-mdx-expression@2.0.1/license) |
| [mdast-util-mdx-jsx](https://github.com/syntax-tree/mdast-util-mdx-jsx#readme) | 3.2.0 | MIT | [license](third_party/licenses/web/mdast-util-mdx-jsx@3.2.0/license) |
| [mdast-util-mdxjs-esm](https://github.com/syntax-tree/mdast-util-mdxjs-esm#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/mdast-util-mdxjs-esm@2.0.1/license) |
| [mdast-util-newline-to-break](https://github.com/syntax-tree/mdast-util-newline-to-break#readme) | 2.0.0 | MIT | [license](third_party/licenses/web/mdast-util-newline-to-break@2.0.0/license) |
| [mdast-util-phrasing](https://github.com/syntax-tree/mdast-util-phrasing#readme) | 4.1.0 | MIT | [license](third_party/licenses/web/mdast-util-phrasing@4.1.0/license) |
| [mdast-util-to-hast](https://github.com/syntax-tree/mdast-util-to-hast#readme) | 13.2.1 | MIT | [license](third_party/licenses/web/mdast-util-to-hast@13.2.1/license) |
| [mdast-util-to-markdown](https://github.com/syntax-tree/mdast-util-to-markdown#readme) | 2.1.2 | MIT | [license](third_party/licenses/web/mdast-util-to-markdown@2.1.2/license) |
| [mdast-util-to-string](https://github.com/syntax-tree/mdast-util-to-string#readme) | 4.0.0 | MIT | [license](third_party/licenses/web/mdast-util-to-string@4.0.0/license) |
| [micromark-core-commonmark](https://github.com/micromark/micromark/tree/main#readme) | 2.0.3 | MIT | [license](third_party/licenses/web/micromark-core-commonmark@2.0.3/license) |
| [micromark-extension-gfm-autolink-literal](https://github.com/micromark/micromark-extension-gfm-autolink-literal#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-autolink-literal@2.1.0/license) |
| [micromark-extension-gfm-footnote](https://github.com/micromark/micromark-extension-gfm-footnote#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-footnote@2.1.0/license) |
| [micromark-extension-gfm-strikethrough](https://github.com/micromark/micromark-extension-gfm-strikethrough#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-strikethrough@2.1.0/license) |
| [micromark-extension-gfm-table](https://github.com/micromark/micromark-extension-gfm-table#readme) | 2.1.1 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-table@2.1.1/license) |
| [micromark-extension-gfm-tagfilter](https://github.com/micromark/micromark-extension-gfm-tagfilter#readme) | 2.0.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-tagfilter@2.0.0/license) |
| [micromark-extension-gfm-task-list-item](https://github.com/micromark/micromark-extension-gfm-task-list-item#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm-task-list-item@2.1.0/license) |
| [micromark-extension-gfm](https://github.com/micromark/micromark-extension-gfm#readme) | 3.0.0 | MIT | [license](third_party/licenses/web/micromark-extension-gfm@3.0.0/license) |
| [micromark-factory-destination](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-factory-destination@2.0.1/license) |
| [micromark-factory-label](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-factory-label@2.0.1/license) |
| [micromark-factory-space](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-factory-space@2.0.1/license) |
| [micromark-factory-title](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-factory-title@2.0.1/license) |
| [micromark-factory-whitespace](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-factory-whitespace@2.0.1/license) |
| [micromark-util-character](https://github.com/micromark/micromark/tree/main#readme) | 2.1.1 | MIT | [license](third_party/licenses/web/micromark-util-character@2.1.1/license) |
| [micromark-util-chunked](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-chunked@2.0.1/license) |
| [micromark-util-classify-character](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-classify-character@2.0.1/license) |
| [micromark-util-combine-extensions](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-combine-extensions@2.0.1/license) |
| [micromark-util-decode-numeric-character-reference](https://github.com/micromark/micromark/tree/main#readme) | 2.0.2 | MIT | [license](third_party/licenses/web/micromark-util-decode-numeric-character-reference@2.0.2/license) |
| [micromark-util-decode-string](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-decode-string@2.0.1/license) |
| [micromark-util-encode](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-encode@2.0.1/license) |
| [micromark-util-html-tag-name](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-html-tag-name@2.0.1/license) |
| [micromark-util-normalize-identifier](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-normalize-identifier@2.0.1/license) |
| [micromark-util-resolve-all](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-resolve-all@2.0.1/license) |
| [micromark-util-sanitize-uri](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-sanitize-uri@2.0.1/license) |
| [micromark-util-subtokenize](https://github.com/micromark/micromark/tree/main#readme) | 2.1.0 | MIT | [license](third_party/licenses/web/micromark-util-subtokenize@2.1.0/license) |
| [micromark-util-symbol](https://github.com/micromark/micromark/tree/main#readme) | 2.0.1 | MIT | [license](third_party/licenses/web/micromark-util-symbol@2.0.1/license) |
| [micromark-util-types](https://github.com/micromark/micromark/tree/main#readme) | 2.0.2 | MIT | [license](third_party/licenses/web/micromark-util-types@2.0.2/license) |
| [micromark](https://github.com/micromark/micromark/tree/main#readme) | 4.0.2 | MIT | [license](third_party/licenses/web/micromark@4.0.2/license) |
| [ms](https://github.com/vercel/ms#readme) | 2.1.3 | MIT | [license.md](third_party/licenses/web/ms@2.1.3/license.md) |
| [parse-entities](https://github.com/wooorm/parse-entities#readme) | 4.0.2 | MIT | [license](third_party/licenses/web/parse-entities@4.0.2/license) |
| [property-information](https://github.com/wooorm/property-information#readme) | 7.2.0 | MIT | [license](third_party/licenses/web/property-information@7.2.0/license) |
| [react-dom](https://react.dev/) | 19.2.7 | MIT | [LICENSE](third_party/licenses/web/react-dom@19.2.7/LICENSE) |
| [react-markdown](https://github.com/remarkjs/react-markdown#readme) | 10.1.0 | MIT | [license](third_party/licenses/web/react-markdown@10.1.0/license) |
| [react-router](https://github.com/remix-run/react-router#readme) | 8.3.0 | MIT | [LICENSE.md](third_party/licenses/web/react-router@8.3.0/LICENSE.md) |
| [react](https://react.dev/) | 19.2.7 | MIT | [LICENSE](third_party/licenses/web/react@19.2.7/LICENSE) |
| [remark-breaks](https://github.com/remarkjs/remark-breaks#readme) | 4.0.0 | MIT | [license](third_party/licenses/web/remark-breaks@4.0.0/license) |
| [remark-gfm](https://github.com/remarkjs/remark-gfm#readme) | 4.0.1 | MIT | [license](third_party/licenses/web/remark-gfm@4.0.1/license) |
| [remark-parse](https://remark.js.org) | 11.0.0 | MIT | [license](third_party/licenses/web/remark-parse@11.0.0/license) |
| [remark-rehype](https://github.com/remarkjs/remark-rehype#readme) | 11.1.2 | MIT | [license](third_party/licenses/web/remark-rehype@11.1.2/license) |
| [remark-stringify](https://remark.js.org) | 11.0.0 | MIT | [license](third_party/licenses/web/remark-stringify@11.0.0/license) |
| [scheduler](https://react.dev/) | 0.27.0 | MIT | [LICENSE](third_party/licenses/web/scheduler@0.27.0/LICENSE) |
| [space-separated-tokens](https://github.com/wooorm/space-separated-tokens#readme) | 2.0.2 | MIT | [license](third_party/licenses/web/space-separated-tokens@2.0.2/license) |
| [stringify-entities](https://github.com/wooorm/stringify-entities#readme) | 4.0.4 | MIT | [license](third_party/licenses/web/stringify-entities@4.0.4/license) |
| [style-to-js](https://github.com/remarkablemark/style-to-js#readme) | 1.1.21 | MIT | [LICENSE](third_party/licenses/web/style-to-js@1.1.21/LICENSE) |
| [style-to-object](https://github.com/remarkablemark/style-to-object#readme) | 1.0.14 | MIT | [LICENSE](third_party/licenses/web/style-to-object@1.0.14/LICENSE) |
| [trim-lines](https://github.com/wooorm/trim-lines#readme) | 3.0.1 | MIT | [license](third_party/licenses/web/trim-lines@3.0.1/license) |
| [trough](https://github.com/wooorm/trough#readme) | 2.2.0 | MIT | [license](third_party/licenses/web/trough@2.2.0/license) |
| [unified](https://unifiedjs.com) | 11.0.5 | MIT | [license](third_party/licenses/web/unified@11.0.5/license) |
| [unist-util-is](https://github.com/syntax-tree/unist-util-is#readme) | 6.0.1 | MIT | [license](third_party/licenses/web/unist-util-is@6.0.1/license) |
| [unist-util-position](https://github.com/syntax-tree/unist-util-position#readme) | 5.0.0 | MIT | [license](third_party/licenses/web/unist-util-position@5.0.0/license) |
| [unist-util-stringify-position](https://github.com/syntax-tree/unist-util-stringify-position#readme) | 4.0.0 | MIT | [license](third_party/licenses/web/unist-util-stringify-position@4.0.0/license) |
| [unist-util-visit-parents](https://github.com/syntax-tree/unist-util-visit-parents#readme) | 6.0.2 | MIT | [license](third_party/licenses/web/unist-util-visit-parents@6.0.2/license) |
| [unist-util-visit](https://github.com/syntax-tree/unist-util-visit#readme) | 5.1.0 | MIT | [license](third_party/licenses/web/unist-util-visit@5.1.0/license) |
| [vfile-message](https://github.com/vfile/vfile-message#readme) | 4.0.3 | MIT | [license](third_party/licenses/web/vfile-message@4.0.3/license) |
| [vfile](https://github.com/vfile/vfile#readme) | 6.0.3 | MIT | [license](third_party/licenses/web/vfile@6.0.3/license) |
| [zwitch](https://github.com/wooorm/zwitch#readme) | 2.0.4 | MIT | [license](third_party/licenses/web/zwitch@2.0.4/license) |
