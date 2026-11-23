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
package openjpa

import jakarta.persistence.Persistence
import library.Author
import library.Book
import spock.lang.Specification

/** The OpenJPA examples from the blog post, on the shared model. */
class PostExamplesSpec extends Specification {
    def "transactions, a set operation, and direct field access"() {
        given:
        var emf = Persistence.createEntityManagerFactory('library',
            ['jakarta.persistence.jdbc.url': 'jdbc:h2:mem:postExamples;DB_CLOSE_DELAY=-1'])

        when:
        emf.runInTransaction { em ->
            var ann = new Author(name: 'Ann')
            em.persist(ann)
            em.persist(new Book(title: 'Long', pages: 900, author: ann))
            em.persist(new Book(title: 'Short', pages: 100, author: ann))
        }
        var titles = emf.callInTransaction { em ->
            em.createQuery('select b.title from Book b where b.pages > :min', String)
              .setParameter('min', 300)
              .resultList
        }
        var union = emf.callInTransaction { em ->
            em.createQuery('select a.name from Author a union select b.title from Book b', String)
              .resultList
        }
        Long id = emf.callInTransaction { em -> em.createQuery('select a.id from Author a', Long).singleResult }
        emf.runInTransaction { em ->
            var author = em.find(Author, id)
            assert author.@books == null    // lazy field not loaded
            assert author.books.size() == 2 // normal property access loads it

            author.@name = 'Lost update'    // not tracked, so never written
        }
        emf.cache.evictAll()
        var name = emf.callInTransaction { em -> em.createQuery('select a.name from Author a', String).singleResult }

        then:
        titles == ['Long']
        union.sort() == ['Ann', 'Long', 'Short']
        name == 'Ann'

        cleanup:
        emf?.close()
    }
}
