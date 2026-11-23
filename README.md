<!--
  SPDX-License-Identifier: Apache-2.0
  Licensed under the Apache License, Version 2.0; see LICENSE.
-->
# Groovy Persistence Two Ways: OpenJPA and Grails Data

Companion code for the Apache Groovy blog post
[Groovy Persistence Two Ways: OpenJPA and Grails Data](https://groovy.apache.org/blog/groovy-openjpa-grails-data).

One set of JPA-annotated Groovy entity classes runs on two persistence stacks:

* [Apache OpenJPA](https://openjpa.apache.org), for standard Jakarta Persistence
* [Apache Grails](https://grails.apache.org) Data on Hibernate, for finders, compile-time
  checked queries, validation and data services, with no Grails application or Spring Boot

## Layout

| Path | What | Java |
|---|---|---|
| `model/` | The shared `library.Author` and `library.Book` entities, compiled into each subproject that uses them | |
| `openjpa/` | Standard JPA on OpenJPA, with build-time enhancement | 17 |
| `grails-data/` | Grails Data as a plain library, with idiomatic domain classes and a data service | 21 |
| `mixed/` | GORM `where` queries and criteria executed by OpenJPA, through `GormJpql` | 21 |
| `scripts/Library.groovy` | A single-file `@Grab` script with no `persistence.xml` | 17+ |
| `NOTES.md` | Version details, gotchas, provider analysis and follow-up ideas | |

## Running

```
sh gradlew test                      # all subprojects
sh gradlew :openjpa:test             # or one subproject
sh gradlew :grails-data:run          # the Grails Data sample application
groovy scripts/Library.groovy        # the @Grab script
```

You need JDK 17 and 21 available as Gradle toolchains.
The examples use Groovy 6.0.0, a Grails Data 9 snapshot for alignment with Groovy 6, and an
OpenJPA 4.3.0 snapshot to incorporate a few recent bug fixes. Both snapshots come from the
Apache snapshots repository, and versions are set in `gradle.properties`.

## Where to find each part of the post

| Post section | Code |
|---|---|
| Shared entity classes | `model/src/main/groovy/library/` |
| Enhancing Groovy classes | `gradle/enhance.gradle`, `openjpa/src/main/resources/META-INF/persistence.xml` |
| Jakarta Persistence 3.2 in Groovy | `openjpa/src/test/groovy/openjpa/PostExamplesSpec.groovy`, `OpenJpaFeaturesSpec.groovy` |
| One Groovy feature to avoid (`obj.@field`) | `openjpa/src/test/groovy/openjpa/PostExamplesSpec.groovy` |
| Single-file scripts | `scripts/Library.groovy` |
| Grails Data without Grails | `grails-data/src/main/groovy/grailsdata/LibraryApp.groovy`, `grails-data/src/test/groovy/grailsdata/PostExamplesSpec.groovy` |
| Queries checked by the compiler | `grails-data/src/test/groovy/grailsdata/GrailsDataSpec.groovy` |
| Idiomatic domain classes and data services | `grails-data/src/main/groovy/grailsdata/`, `grails-data/src/test/groovy/grailsdata/PostDataServiceSpec.groovy` |
| Mixing the two | `mixed/src/main/groovy/mixed/GormJpql.groovy`, `mixed/src/test/groovy/mixed/` |

## Findings: OpenJPA with Groovy

| Question | Result |
|---|---|
| Does `PCEnhancer` work on groovyc output? | Yes. A `JavaExec` task after `compileGroovy` enhances in place (`gradle/enhance.gradle`). |
| Field access + Groovy `metaClass` | Fine: the generated field is `transient`, so it's ignored. |
| Property access + Groovy `metaClass` | Works; a WARN suggests `@Transient` for `metaClass`. |
| Property-access setter warning | Groovy emits `aload_1; aload_0; swap; putfield` for `this.x = x`; javac emits `aload_0; aload_1; putfield`. OpenJPA's check only recognises javac's form, so Groovy setters get a spurious "accessed directly" WARN. Harmless. |
| Lazy loading, `@CompileStatic` entity | Works. |
| Lazy loading, dynamic Groovy method using own field | Works (compiles to direct `getfield`, which the enhancer rewrites). |
| `obj.@field` read from outside | **Bypasses enhancement**: lazy field reads as `null`. |
| `obj.@field = v` from outside | **Bypasses dirty tracking**: update silently lost. |
| Record as `@Embeddable` (JPA 3.2) | Works. |
| `emf.runInTransaction { em -> }` with a closure | Works. |
| `emf.callInTransaction { em -> 'any value' }` | Works, including non-entity results. |
| JPQL `\|\|`, `left()` | Work. |
| JPQL and Criteria set operations | Work: a typed `union all` returns plain values and keeps duplicates. |
| `@Grab` + `PersistenceConfiguration` script | Works, needs explicit `JDBC_DRIVER` and `DB_CLOSE_DELAY=-1`. No enhancement possible (classes loaded before the EMF; dynamic agent registration throws `UnsupportedOperationException`; plain `openjpa` jar unusable as `-javaagent`), so uses `RuntimeUnenhancedClasses=supported` subclassing: to-one relations load eagerly. |

## Findings: GORM entities on OpenJPA (`mixed/`)

With `grails-datamapping-core` on the compile classpath, GORM's global
`GlobalJpaEntityTransform` turns every `@jakarta.persistence.Entity` class
into a GORM entity, with no GORM imports in the source.

| Question | Result |
|---|---|
| Does the transformed class still enhance? | Yes: it implements `GormEntity`, `GormEntityDirtyCheckable` and `GormValidateable` (322 methods) and OpenJPA's `PCEnhancer` enhances it as usual |
| Does OpenJPA try to persist GORM's trait fields? | No: `$changedProperties` and `skipValidate` are `transient`, and `errors` carries `@jakarta.persistence.Transient`; the metamodel holds only the declared properties |
| Round-trip and lazy loading | Work as for a plain entity |
| GORM-transformed setters | Updates still reach the database through OpenJPA's own tracking |
| GORM change tracking | The `DirtyCheckable` trait records changes on an OpenJPA-loaded instance |
| GORM API (`count()`, `save()`, `validate()`, `hasChanged()`) | `IllegalStateException: No GORM implementation configured`: these delegate to a GORM datastore, which is the missing piece |

### GORM queries executed by OpenJPA

`GormJpql` passes a GORM `DetachedCriteria` to GORM's `JpaQueryBuilder` and
runs the resulting JPQL on an OpenJPA `EntityManager`. A `SimpleMapDatastore`
initialises GORM's metadata and static API only; it holds no data and executes
none of these queries.

| Query feature | Result |
|---|---|
| `where`: comparisons, `&&`/`\|\|`, `!`, like, `in`, ranges, null, property comparison, composition | Works |
| Associations: to-one paths, to-many joins, `size()` | Works |
| Criteria: `gt`, `between`, `or`, nested association, ordering | Works |
| Projections: `count`, `property`, `max`, `sum` | Works |
| Pagination | Works; `GormJpql` applies max and offset |
| `ilike` | Works with a rewrite: the builder emits `like lower(:p)`, but OpenJPA's grammar takes only a parameter or literal as the pattern |
| `distinct` projection | Works with a rewrite: the builder silently omits `DISTINCT` |
| Subquery as a comparison value (`pages > avg(pages)`) | Not supported by the builder |
| Unknown property in `where` | Compile error from GORM: `Cannot query on property "pagez"` |

Caveats: no Grails production code calls `JpaQueryBuilder.buildSelect()` (it
uses the builder for bulk update and delete), so its select path is lightly
exercised. `Book.where { }` must be built outside a Spock condition, whose
rewriting hides the closure from GORM's transform.

## Findings: Grails Data without Grails (`grails-data/`)

The only declared Grails dependency is `grails-data-hibernate7-core`. There is no Grails
application or Spring Boot, and one constructor starts it:

```groovy
new HibernateDatastore(config, Author, Book)
```

| Feature | Result |
|---|---|
| Static API, dynamic finders, `where`, criteria | Works |
| `ilike`, `distinct`, `gtAll` subquery | Works |
| `where { pages > avg(pages) }` | Fails at query time on Hibernate 7; use `gtAll` |
| Constraints and dirty checking | Works |
| `@Service` data services, including `@Query` | Works |
| Mistyped property in `where` | Compile error |
| The shared `library.Author`/`library.Book` sources | Work unchanged, with the full GORM API |

Notes: set `DB_CLOSE_DELAY=-1` for an in-memory H2 database, since the default
configuration loses the schema. Declare the `belongsTo` property explicitly
(`Author author`) when a data service queries it.

See [NOTES.md](NOTES.md) for version details, gotchas and follow-up ideas.
