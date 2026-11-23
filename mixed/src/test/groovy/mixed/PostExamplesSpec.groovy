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

import jakarta.persistence.Persistence
import library.Author
import library.Book
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.Specification

/** The blog post's example of a GORM query run by OpenJPA. */
class PostExamplesSpec extends Specification {
    def "a composed where query returns managed OpenJPA entities"() {
        given:
        var datastore = new SimpleMapDatastore(Author, Book) // GORM metadata and static API only
        var emf = Persistence.createEntityManagerFactory('library',
            ['jakarta.persistence.jdbc.url': 'jdbc:h2:mem:postExamples;DB_CLOSE_DELAY=-1'])
        emf.runInTransaction { em ->
            var ann = new Author(name: 'Ann')
            em.persist(ann)
            em.persist(new Book(title: 'Long', pages: 900, author: ann))
            em.persist(new Book(title: 'Short', pages: 100, author: ann))
        }
        var big = Book.where { pages > 200 }
        var annsBig = big.where { author.name == 'Ann' }
        var manager = emf.createEntityManager()

        when:
        var results = GormJpql.list(manager, annsBig)

        then:
        results*.title == ['Long']
        manager.contains(results[0])

        cleanup:
        manager?.close(); emf?.close(); datastore?.close()
    }
}
