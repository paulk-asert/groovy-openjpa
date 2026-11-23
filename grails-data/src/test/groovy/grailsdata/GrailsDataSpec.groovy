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
package grailsdata

import grails.gorm.DetachedCriteria
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilerConfiguration
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GrailsDataSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore

    def setupSpec() {
        datastore = new HibernateDatastore(LibraryApp.CONFIG, Author, Book)
        Author.withTransaction {
            def ann = new Author(name: 'Ann')
            ann.addToBooks(new Book(title: 'Short', pages: 100))
            ann.addToBooks(new Book(title: 'Long', pages: 900))
            ann.save(failOnError: true)
            def bob = new Author(name: 'Bob')
            bob.addToBooks(new Book(title: 'Middle', pages: 400))
            bob.save(failOnError: true, flush: true)
        }
    }

    private static <T> T tx(Closure<T> work) { Book.withTransaction { work() } }

    def "no Grails or Spring Boot classes are needed at runtime"() {
        when:
        Class.forName('grails.boot.GrailsApp')

        then:
        thrown(ClassNotFoundException)

        when:
        Class.forName('org.springframework.boot.SpringApplication')

        then:
        thrown(ClassNotFoundException)
    }

    def "static API and dynamic finders"() {
        expect:
        tx { Book.count() } == 3
        tx { Book.findByTitle('Long').pages } == 900
        tx { Book.findAllByPagesGreaterThan(300)*.title.sort() } == ['Long', 'Middle']
    }

    def "where query with association and composition"() {
        given:
        def big = Book.where { pages > 300 }
        def annsBig = big.where { author.name == 'Ann' }

        expect:
        tx { big.count() } == 2
        tx { annsBig.list()*.title } == ['Long']
    }

    def "ilike, distinct and a subquery comparison"() {
        given:
        def insensitive = new DetachedCriteria(Book).build { ilike 'title', 'LO%' }
        def authors = new DetachedCriteria(Book).build { projections { distinct 'author' } }
        def aboveAverage = new DetachedCriteria(Book).build {
            gtAll 'pages', { projections { avg 'pages' } }
        }

        expect:
        tx { insensitive.list()*.title } == ['Long']
        tx { authors.list().size() } == 2
        tx { aboveAverage.list()*.title } == ['Long']
    }

    def "the where shorthand for an aggregate subquery fails on Hibernate 7"() {
        given:
        def aboveAverage = Book.where { pages > avg(pages) }

        when:
        tx { aboveAverage.list() }

        then:
        thrown(org.hibernate.query.QueryArgumentException)
    }

    def "constraints validate"() {
        when:
        def book = new Book(title: '', pages: 0, author: new Author(name: 'X'))

        then:
        !tx { book.validate() }
        book.errors.hasFieldErrors('title')
        book.errors.hasFieldErrors('pages')
    }

    def "dirty checking"() {
        expect:
        tx {
            def book = Book.findByTitle('Short')
            assert !book.isDirty()
            book.title = 'Shorter'
            assert book.dirtyPropertyNames == ['title']
            book.discard()
            true
        }
    }

    def "data service implemented at compile time"() {
        given:
        def catalog = datastore.getService(BookCatalog)
        def ann = tx { Author.findByName('Ann') }

        expect:
        catalog.findByPagesGreaterThan(300)*.title.sort() == ['Long', 'Middle']
        catalog.countByAuthor(ann) == 2
        catalog.longerThan(300)*.title == ['Long', 'Middle']
    }

    def "a mistyped where property fails compilation"() {
        given:
        def config = new CompilerConfiguration()

        when:
        new GroovyShell(getClass().classLoader, config).parse('''
            import grailsdata.Book
            Book.where { pagez > 1 }
        ''')

        then:
        def e = thrown(CompilationFailedException)
        e.message.contains('Cannot query on property "pagez"')
    }
}
