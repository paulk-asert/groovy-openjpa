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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/** The same library.Author and library.Book sources that OpenJPA enhances, on GORM for Hibernate. */
class SameJpaEntitiesSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore =
        new HibernateDatastore(LibraryApp.CONFIG + ['dataSource.url': 'jdbc:h2:mem:jpa;DB_CLOSE_DELAY=-1'],
            library.Author, library.Book)

    def "the JPA-annotated classes get the full GORM API"() {
        given:
        def big = library.Book.where { pages > 300 && author.name == 'Ann' }

        when:
        library.Author.withTransaction {
            def ann = new library.Author(name: 'Ann').save(failOnError: true)
            new library.Book(title: 'Long', pages: 900, author: ann).save(failOnError: true)
            new library.Book(title: 'Short', pages: 100, author: ann).save(failOnError: true, flush: true)
        }

        then:
        library.Book.withTransaction { library.Book.count() } == 2
        library.Book.withTransaction { library.Book.findByTitle('Short').author.name } == 'Ann'
        library.Book.withTransaction { big.list()*.title } == ['Long']
    }
}
