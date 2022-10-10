<img src="https://i.imgur.com/GH71uSi.png" title="zalky" align="right" width="250"/>

# Build-clj

Build tasks via
[`tools.build`](https://github.com/clojure/tools.build):

1. `jar`: builds a library jar, but also adds license and readme files
    to the `META-INF` directory of your jar, as well as license and
    project description attributes to the `pom.xml` (similar to
    leiningen)

2. `uber`: same semantics as `jar`, just produces an uberjar instead.

3. `install`: installs a jar to the local Maven repo.

4. `deploy`: uses
   [`io.zalky/deps-deploy`](https://github.com/zalky/deps-deploy)
   (fork of [`slipset/deps-deploy`](https://github.com/slipset/deps-deploy))
   that signs your releases by default, but doesn't ask for or load
   your gpg password into memory, instead delegating it to the gpg
   process

There are also some useful functions in the `io.zalky.build.jar` namespace
for updating files in jars.

## Usage

Just include the following alias in your `deps.edn`:

```clj
{:build {:deps       {io.zalky/build-clj {:git/url "https://github.com/zalky/build-clj.git"
                                          :git/sha "e55d0063bc2ac6297e16a2edf026982de16432fb"}}
         :ns-default io.zalky.build}}
```

Or if you prefer the default `slipset/deps-deploy` target:

```clj
{:build {:deps       {slipset/deps-deploy {:mvn/version "0.2.0"}
                      io.zalky/build-clj  {:git/url    "https://github.com/zalky/build-clj.git"
                                           :git/sha    "e55d0063bc2ac6297e16a2edf026982de16432fb"
                                           :exclusions [io.zalky/deps-deploy]}}
         :ns-default io.zalky.build}}
```

You can then run a build task like so:

```clj
clojure -T:build <task> <list of options>
```

For example, to build a library jar:

```clj
clojure -T:build jar :lib org.your/project :version "\"0.1.0\"" :description "\"Beep boop\"" :license :apache
```

## Task Options

All tasks have two required parameters:

1. **`:lib`**: Release group id and artifact id, ex:
`io.zalky/build-clj`

2. **`:version`**: Release version, ex: `0.1.1`, `1.0.2-SNAPSHOT`

The `jar` and `uber` task have the following additional parameters:

1. **`:jar-dir`**: Directory of output jar, default is `target`.

2. **`:basis`**: Options passed to `clojure.tools.build/create-basis`
   subtasks. Default basis created if not provided.

3. **`:class-dir`**: Intermediary directory where contents of the jar
   are collected before archiving. This gives you the opportunity to
   add additional things if you are using non-Clojure build
   frameworks, like `make`. Default is `<:jar-dir>/classes/`

4. **`:src-dirs`**: An explicit list of source directories to include
   in the jar. If not specified, everything on the classpath will be
   included.

5. **`:meta-inf-files`**: Seq of regex patterns to match against files
   in the project root, by default `["(?i)license"
   "(?i)readme"]`. Matched files will be included in the META-INF
   folder of the jar.

6. **`:license`**: One of the valid licenses enumerated by the keys of
   `io.zalky.build/licenses`. The attributes of this license will be
   added to the `pom.xml` file. This license should also match the
   file that is included via the `:meta-inf-files` patterns.

   The currently configured licenses are `:apache`, `:mit`, `:epl-1`,
   and `:epl-2`.

7. **`:description`**: Project description added to `pom.xml`

The `uber` task additionally has the following required parameters:

1. **`:main`**: Namespace with `-main` function. Namespace must be
   configured with `:gen-class`.

The `install` and `deploy` task have the following optional
parameter:

1. **`:jar-dir`**: Directory where to find the jar to deploy, default
   is `target`.

The `deploy` task has the following optional parameters:

1. **`:repository`**: Conforms to the semantics of
   `deps-deploy.deps-deploy/deploy`. If left unspecified, Clojars is
   assumed.

2. **`:sign-releases?`**: Whether to sign releases with your gpg
   key. If you are using `io.zalky/deps-deploy`, then defaults to
   true. If you do not want to sign your releases, or don't have GPG
   configured, you need to disable release signing with
   `:sign-releases?  false`.

3. **`:sign-key-id`**: The gpg signing key to use. If left
   unspecified, default key is used.

## License

Distributed under the terms of the Apache License 2.0.

