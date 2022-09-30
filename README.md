# Build-clj

Build tasks based in
[`tools.build`](https://github.com/clojure/tools.build)

1. `jar`: adds license and readme files to the `META-INF` directory of
    your jar, as well as adds license and project description
    attributes to the `pom.xml` (similar to leiningen)

2. `deploy`: uses a fork of
   [`slipset/deps-deploy`](https://github.com/slipset/deps-deploy)
   that signs your releases by default, but doesn't ask for or load
   your gpg password into memory, instead delegating it to the gpg
   process

3. `install`

There are also some useful functions in the `io.zalky.build` namespace
for updating files in jars.

## Usage

Just include an alias like the following in your project `deps.edn`:

```clj
{:build {:deps       {io.zalky/build-clj {:git/url "https://github.com/zalky/build-clj.git"
                                          :git/sha "67ee63f5f4e77165c378369f781275d5aca39747"}}
         :ns-default io.zalky.build}}
```

Or if you prefer the default `slipset/deps-deploy`:

```clj
{:build {:deps       {slipset/deps-deploy {:mvn/version "0.2.0"}
                      io.zalky/build-clj  {:git/url    "https://github.com/zalky/build-clj.git"
                                           :git/sha    "67ee63f5f4e77165c378369f781275d5aca39747"
                                           :exclusions [io.zalky/deps-deploy]}}
         :ns-default io.zalky.build}}
```

Then build your jar like so:

```clj
clojure -T:build jar :lib org.your/project :version "\"0.1.0\"" :description "\"Beep boop\"" :license :apache
```
The currently configured licenses are `:apache`, `:mit`, `:epl-1`, and
`:epl-2`. If you want other root files besides your license and readme included in
the `META-INF` directory, you can configure a seq of regex patterns to
match against via the `:meta-inf-files` option. The default
is `["(?i)license" "(?i)readme"]`.

If you do not want to sign your releases, or don't have GPG
configured, you need to disable release signing with `:sign-releases?
false`.

## License

Distributed under the terms of the Apache License 2.0.

