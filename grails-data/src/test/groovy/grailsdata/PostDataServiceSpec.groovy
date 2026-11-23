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
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/** The blog post's data service and subquery examples on the idiomatic domain classes. */
class PostDataServiceSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
        ['dataSource.url': 'jdbc:h2:mem:postService;DB_CLOSE_DELAY=-1', 'dataSource.dbCreate': 'create-drop'],
        Author, Book)

    def setupSpec() {
        Author.withTransaction {
            def ann = new Author(name: 'Ann')
            ann.addToBooks(new Book(title: 'Long', pages: 900))
            ann.addToBooks(new Book(title: 'Short', pages: 100))
            ann.save(failOnError: true, flush: true)
        }
    }

    def "data service"() {
        given:
        var catalog = datastore.getService(BookCatalog)

        expect:
        catalog.longerThan(300)*.title == ['Long']
    }

    def "gtAll subquery"() {
        given:
        var aboveAverage = new DetachedCriteria(Book).build {
            gtAll 'pages', { projections { avg 'pages' } }
        }

        expect:
        Book.withTransaction { aboveAverage.list()*.title } == ['Long']
    }
}
