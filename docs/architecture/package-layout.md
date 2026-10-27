# STEP 2.2: Internal package layout

The project remains a single Maven artifact. Logical modules below are package
groups under `kr.soyuo.serox`; the Maven source/resource layout remains standard.

```text
kr.soyuo.serox
├── app                       # application entry and composition root
├── ui                        # JavaFX presentation
├── core
│   ├── application           # use cases and browser lifecycle
│   ├── error                 # shared typed error contracts
│   ├── event                 # application events and subscriptions
│   ├── model                 # immutable cross-module values
│   └── port.storage          # repository interfaces
├── browser
│   ├── api                   # public engine capabilities
│   ├── network               # HTTP, URL, redirects, resource policy
│   ├── html                  # tokenizer and tree builder
│   ├── dom                   # document and node model
│   ├── css                   # CSS parsing and style resolution
│   ├── layout                # block/inline layout and box model
│   └── render                # display list and JavaFX Canvas backend
├── search
│   ├── api                   # SearchProvider and search use-case contracts
│   ├── provider              # Brave adapter and response mapping
│   ├── fetch                # bounded page retrieval
│   ├── extract              # main-content and evidence extraction
│   └── cache                 # search-result cache adapter
├── ai
│   ├── api                   # AI provider and analysis contracts
│   ├── provider              # Claude adapter
│   ├── context               # consent-aware context construction
│   ├── citation              # citation validation and source mapping
│   └── conversation          # AI conversation management
├── mcp
│   ├── server                # server lifecycle and transport
│   ├── tool                  # tool definitions and dispatch
│   └── security              # authorization and input validation
└── storage
    ├── sqlite                # database lifecycle and migrations
    ├── history               # history repository adapter
    ├── bookmark              # bookmark repository adapter
    ├── session               # session/tab repository adapter
    ├── settings              # settings repository adapter
    ├── conversation          # conversation repository adapter
    └── searchcache           # search cache repository adapter
```

Packages are created only when they have an owner and documented boundary. A
`package-info.java` keeps an intentionally empty package visible to Git and
records that package's responsibility. Public contracts are added in STEP 2.3;
engine, provider, and database implementations remain non-exported details.

`module-info.java` continues to export only the JavaFX application entry package
needed by `jpackage`. Packages inside this Maven artifact communicate through
public Java types without becoming separate JPMS modules.
