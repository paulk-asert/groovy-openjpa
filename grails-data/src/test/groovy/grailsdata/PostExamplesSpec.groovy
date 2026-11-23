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

import library.Author
import library.Book
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.Specification

/** The blog post's Grails Data examples on the shared JPA-annotated model. */
class PostExamplesSpec extends Specification {
    def "the shared classes get the GORM API"() {
        given:
        var config = ['dataSource.url'     : 'jdbc:h2:mem:postExamples;DB_CLOSE_DELAY=-1',
                      'dataSource.dbCreate': 'create-drop']

        expect:
        new HibernateDatastore(config, Author, Book).withCloseable {
            Author.withTransaction {
                var ann = new Author(name: 'Ann').save(failOnError: true)
                new Book(title: 'Long', pages: 900, author: ann).save(failOnError: true)
                new Book(title: 'Short', pages: 100, author: ann).save(failOnError: true, flush: true)
            }
            Examples.run()
        }
    }

    // outside the spec, as Spock's rewriting of conditions hides closures from the where transform
    static class Examples {
        static boolean run() {
            Book.withTransaction {
                assert Book.count() == 2
                assert Book.findByTitle('Short').author.name == 'Ann'
                assert Book.where { pages > 300 && author.name == 'Ann' }.list()*.title == ['Long']
            }
            true
        }
    }
}
