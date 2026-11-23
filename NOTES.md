<!--
  SPDX-License-Identifier: Apache-2.0
  Licensed under the Apache License, Version 2.0; see LICENSE.
-->
# Notes

Details that were useful while building these examples, but which are too
specialised for the README's findings tables.

## Versions

- OpenJPA `4.3.0-SNAPSHOT` includes fixes made after 4.2.0:
  [OPENJPA-3090](https://issues.apache.org/jira/browse/OPENJPA-3090) (`callInTransaction` with non-entity results),
  [OPENJPA-3091](https://issues.apache.org/jira/browse/OPENJPA-3091) (JPQL set operation results) and
  [OPENJPA-3092](https://issues.apache.org/jira/browse/OPENJPA-3092) (Criteria set operation results).
  Tested with build `20261122.145129-5`.
- Grails Data `9.0.0-SNAPSHOT` is used because Grails Data 9 is built on Groovy 6.
  The examples have not been run on Grails Data 8.
- Snapshots change and expire, so pin released versions when they are available.

## OpenJPA with Groovy

- With property access, OpenJPA warns that Groovy setters access fields directly.
  Groovy emits `aload_1; aload_0; swap; putfield` for `this.x = x`, and OpenJPA's check
  recognises only javac's `aload_0; aload_1; putfield`. The warning is harmless.
- With property access, OpenJPA also suggests `@Transient` for `metaClass`.
- `@Grab` scripts can't use build-time enhancement, and dynamic agent registration
  throws `UnsupportedOperationException`. The plain `openjpa` jar can't be used as a
  `-javaagent`, so scripts use `RuntimeUnenhancedClasses=supported`.

## GORM entities on OpenJPA

- GORM's `$changedProperties` and `skipValidate` trait fields are `transient`, and
  `errors` has `@jakarta.persistence.Transient`, so OpenJPA doesn't persist them.
- `JpaQueryBuilder` emits `like lower(:p)` for `ilike`, but OpenJPA's grammar accepts
  only a parameter or literal as a pattern. `GormJpql` lower-cases the value instead.
- `JpaQueryBuilder` omits `DISTINCT` for a distinct property projection. `GormJpql` adds it.
- A subquery as a comparison value fails in the builder with `ConverterNotFoundException`.
- Grails production code uses `JpaQueryBuilder` for bulk update and delete, not `buildSelect()`.

## Grails Data without Grails

- With in-memory H2, set `DB_CLOSE_DELAY=-1`. Without it, `new HibernateDatastore(Author, Book)`
  failed with `Table "AUTHOR" not found`.
- Declare a `belongsTo` property explicitly (`Author author`) if a data service queries it.
- A data service `@Query` such as `${b.author.name}` fails static type checking;
  a direct property such as `${b.pages}` works.
- `where { pages > avg(pages) }` compiles but fails at query time on Hibernate 7. Use `gtAll`.

## Testing

- Build `Book.where { }` in a Spock `given:` block. Spock's rewriting of `expect:` conditions
  hides the closure from GORM's transform.
- GORM's static API registration is per JVM, so `grails-data` and `mixed` use `forkEvery = 1`.

## What a full OpenJPA provider for Grails Data would involve

- `grails-datastore-core` and `grails-datamapping-core` total about 75,000 lines with one
  Hibernate import.
- `grails-data-hibernate7/core` is about 37,000 lines with 830 `org.hibernate` imports, 232 of
  them from `org.hibernate.mapping`. Its query layer is about 7,800 lines.
- `grails-data-simple` shows the size of a minimal datastore: 6 files, about 1,800 lines.
- `grails-datamapping-tck` has 67 specs and would measure compatibility.
- The historic `grails-datastore-jpa` module (2010 to 2014, about 2,700 lines) took this approach
  with Hibernate 3 as the provider.

## Ideas worth following up

- A Groovy compile hook (`ClassgenCallback` or similar) that runs `PCEnhancer` on bytecode in memory, giving scripts proper enhancement.
- A type-checking extension that validates JPQL strings against `@Entity` classes at compile time.
- An AST transform generating the static metamodel (`Author_`), since annotation processors don't see Groovy sources.
