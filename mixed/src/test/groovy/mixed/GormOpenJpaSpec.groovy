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
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import jakarta.persistence.PersistenceUnitUtil
import org.apache.openjpa.enhance.PersistenceCapable
import org.grails.datastore.gorm.GormEntity
import spock.lang.Shared
import spock.lang.Specification

class GormOpenJpaSpec extends Specification {

    @Shared EntityManagerFactory emf = Persistence.createEntityManagerFactory('library')

    def cleanupSpec() { emf?.close() }

    def "the class is both a GORM entity and an enhanced OpenJPA entity"() {
        expect:
        GormEntity.isAssignableFrom(Author)
        PersistenceCapable.isAssignableFrom(Author)
    }

    def "OpenJPA persists only the declared properties, not the GORM trait fields"() {
        expect:
        emf.metamodel.entity(Author).attributes*.name.sort() == ['books', 'id', 'name']
        emf.metamodel.entity(Book).attributes*.name.sort() == ['author', 'id', 'pages', 'title']
    }

    def "entities round-trip, with lazy loading"() {
        given:
        def em = emf.createEntityManager()
        em.transaction.begin()
        def author = new Author(name: 'Ann')
        em.persist(author)
        ['A', 'B'].each { em.persist(new Book(title: it, author: author)) }
        em.transaction.commit()
        em.close()

        when:
        em = emf.createEntityManager()
        def loaded = em.find(Author, author.id)
        PersistenceUnitUtil util = emf.persistenceUnitUtil
        def lazyBefore = util.isLoaded(loaded, 'books')
        def titles = loaded.books*.title.sort()

        then:
        loaded.name == 'Ann'
        !lazyBefore
        titles == ['A', 'B']
        util.isLoaded(loaded, 'books')

        cleanup:
        em?.close()
    }

    def "GORM's change tracking runs on an OpenJPA-loaded instance"() {
        given:
        def em = emf.createEntityManager()
        em.transaction.begin()
        def author = new Author(name: 'Bob')
        em.persist(author)
        em.transaction.commit()
        em.clear()

        when:
        def loaded = em.find(Author, author.id)
        loaded.trackChanges()
        loaded.name = 'Robert'
        // the DirtyCheckable trait's own implementation; GormEntityDirtyCheckable
        // overrides hasChanged() to go through a GORM datastore
        def plain = Class.forName('org.grails.datastore.mapping.dirty.checking.DirtyCheckable$Trait$Helper')

        then:
        plain.hasChanged(loaded, 'name')
        !plain.hasChanged(loaded, 'id')
        loaded.getOriginalValue('name') == 'Bob'

        cleanup:
        em?.close()
    }

    def "an update through a GORM-transformed setter is flushed by OpenJPA"() {
        given:
        def em = emf.createEntityManager()
        em.transaction.begin()
        def author = new Author(name: 'Di')
        em.persist(author)
        em.transaction.commit()
        em.close()

        when:
        em = emf.createEntityManager()
        em.transaction.begin()
        em.find(Author, author.id).name = 'Diana'
        em.transaction.commit()
        em.close()
        em = emf.createEntityManager()

        then:
        em.find(Author, author.id).name == 'Diana'

        cleanup:
        em?.close()
    }

    def "GORM methods that delegate to a datastore fail without one"() {
        given:
        def author = new Author(name: 'Cy')

        expect:
        failsWithoutDatastore { author.hasChanged('name') }
        failsWithoutDatastore { author.validate() }
        failsWithoutDatastore { author.save() }
    }

    private static boolean failsWithoutDatastore(Closure call) {
        try {
            call()
            false
        } catch (IllegalStateException e) {
            e.message.startsWith('No GORM implementation configured')
        }
    }

    def "GORM's own persistence API needs a GORM datastore"() {
        when:
        Author.count()

        then:
        def e = thrown(IllegalStateException)
        println "Author.count() without a datastore: $e.message"
    }
}
