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

import org.grails.orm.hibernate.HibernateDatastore

class LibraryApp {
    static final Map<String, Object> CONFIG = [
        'dataSource.url'     : 'jdbc:h2:mem:library;DB_CLOSE_DELAY=-1',
        'dataSource.dbCreate': 'create-drop',
    ]

    static void main(String[] args) {
        new HibernateDatastore(CONFIG, Author, Book).withCloseable { datastore ->
            Author.withTransaction {
                def dierk = new Author(name: 'Dierk').save(failOnError: true)
                [['Groovy in Action', 912], ['ReGinA', 496]].each { title, pages ->
                    dierk.addToBooks(new Book(title: title, pages: pages))
                }
                dierk.save(flush: true, failOnError: true)
            }
            Book.withTransaction {
                def big = Book.where { pages > 500 && author.name == 'Dierk' }
                big.list().each { println "$it.title by $it.author.name" }
            }
        }
    }
}
