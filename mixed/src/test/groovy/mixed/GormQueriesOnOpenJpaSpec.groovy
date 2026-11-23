/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mixed

import library.Author
import library.Book
import grails.gorm.DetachedCriteria
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.Shared
import spock.lang.Specification

/**
 * GORM builds the queries and their JPQL; OpenJPA runs them against H2. The
 * in-memory datastore only initialises GORM's metadata and static API: it
 * holds no data and executes none of these queries.
 */
class GormQueriesOnOpenJpaSpec extends Specification {

    @Shared SimpleMapDatastore datastore = new SimpleMapDatastore(Author, Book)
    @Shared EntityManagerFactory emf = Persistence.createEntityManagerFactory('library',
        ['jakarta.persistence.jdbc.url': 'jdbc:h2:mem:mixedQueries;DB_CLOSE_DELAY=-1'])
    EntityManager em

    def setupSpec() {
        def em = emf.createEntityManager()
        em.transaction.begin()
        def ann = new Author(name: 'Ann'), bob = new Author(name: 'Bob')
        [ann, bob].each { em.persist(it) }
        [['Alpha', 100, ann], ['Beta', 250, ann], ['Gamma', 400, bob], ['Delta', null, bob]].each { t, p, a ->
            em.persist(new Book(title: t, pages: p, author: a))
        }
        em.transaction.commit()
        em.close()
    }

    def setup() { em = emf.createEntityManager() }
    def cleanup() { em?.close() }
    def cleanupSpec() { emf?.close(); datastore?.close() }

    private List<String> titles(DetachedCriteria<Book> c) { GormJpql.list(em, c)*.title.sort() }
    private List<String> names(DetachedCriteria<Author> c) { GormJpql.list(em, c)*.name.sort() }
    private static DetachedCriteria<Book> books() { new DetachedCriteria<>(Book) }

    // Built in given: blocks because Spock rewrites expressions in conditions,
    // which hides their closures from GORM's where transform.

    def "where queries"() {
        given:
        def greater = Book.where { pages > 200 }
        def logical = Book.where { (pages > 300 && title == 'Gamma') || title == 'Alpha' }
        def like = Book.where { title ==~ 'A%' }
        def inList = Book.where { title in ['Alpha', 'Beta'] }
        def range = Book.where { pages in 100..300 }
        def isNull = Book.where { pages == null }
        def negated = Book.where { !(title == 'Alpha') }

        expect:
        titles(greater) == ['Beta', 'Gamma']
        titles(logical) == ['Alpha', 'Gamma']
        titles(like) == ['Alpha']
        titles(inList) == ['Alpha', 'Beta']
        titles(range) == ['Alpha', 'Beta']
        titles(isNull) == ['Delta']
        titles(negated) == ['Beta', 'Delta', 'Gamma']
    }

    def "where queries across associations"() {
        given:
        def toOne = Book.where { author.name == 'Ann' }
        def toMany = Author.where { books.title == 'Gamma' }
        def size = Author.where { books.size() == 2 }

        expect:
        titles(toOne) == ['Alpha', 'Beta']
        names(toMany) == ['Bob']
        names(size) == ['Ann', 'Bob']
    }

    def "where queries compose"() {
        given:
        def big = Book.where { pages > 200 }
        def composed = big.where { title == 'Gamma' }

        expect:
        titles(big) == ['Beta', 'Gamma']
        titles(composed) == ['Gamma']
    }

    def "a where query comparing two properties"() {
        given:
        def query = Book.where { pages > id }
        def info = GormJpql.translate(query)

        expect:
        info.query.contains('book.pages>book.id')
        titles(query) == ['Alpha', 'Beta', 'Gamma']
    }

    def "criteria"() {
        expect:
        GormJpql.list(em, books().build { gt 'pages', 200; order 'title', 'desc' })*.title == ['Gamma', 'Beta']
        titles(books().build { between 'pages', 100, 300 }) == ['Alpha', 'Beta']
        titles(books().build { or { eq 'title', 'Alpha'; eq 'title', 'Beta' } }) == ['Alpha', 'Beta']
        titles(books().build { author { eq 'name', 'Bob' } }) == ['Delta', 'Gamma']
    }

    def "case-insensitive like, with the pattern lower-cased for portable JPQL"() {
        given:
        def info = GormJpql.translate(books().build { ilike 'title', 'A%' })

        expect:
        info.query.endsWith('(lower(book.title) like :p1)')
        info.parameters == ['a%']
        titles(books().build { ilike 'title', 'A%' }) == ['Alpha']
    }

    def "projections"() {
        expect:
        GormJpql.list(em, books().build { projections { count() } }) == [4L]
        GormJpql.list(em, books().build { projections { property 'title' }; order 'title' }) ==
            ['Alpha', 'Beta', 'Delta', 'Gamma']
        GormJpql.list(em, books().build { projections { max 'pages'; sum 'pages' } })*.toList() == [[400, 750L]]
        GormJpql.list(em, books().build { projections { distinct 'author' } })*.name.sort() == ['Ann', 'Bob']
    }

    def "pagination"() {
        given:
        def ordered = books().build { order 'title' }

        expect:
        GormJpql.list(em, ordered.max(2))*.title == ['Alpha', 'Beta']
        GormJpql.list(em, ordered.max(2).offset(2))*.title == ['Delta', 'Gamma']
    }

    def "results are managed OpenJPA entities with lazy associations"() {
        given:
        def query = Book.where { title == 'Beta' }

        when:
        def book = GormJpql.list(em, query).first()

        then:
        em.contains(book)
        !emf.persistenceUnitUtil.isLoaded(book, 'author')
        book.author.name == 'Ann'
    }

    def "a where query on an unknown property fails at compile time"() {
        when:
        new GroovyShell(getClass().classLoader).evaluate '''
            import library.Book
            Book.where { pagez > 200 }
        '''

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Cannot query on property "pagez" - no such property on class library.Book')
    }

    def "a subquery as a comparison value is not supported by the JPQL builder"() {
        given:
        def query = Book.where { pages > avg(pages) }

        when:
        GormJpql.translate(query)

        then:
        thrown(org.springframework.core.convert.ConverterNotFoundException)
    }
}
