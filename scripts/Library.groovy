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

// Single-file OpenJPA: no persistence.xml, no build, entities defined right here.
@GrabResolver(name = 'apache-snapshots', root = 'https://repository.apache.org/content/groups/snapshots')
@Grab('org.apache.openjpa:openjpa:4.3.0-SNAPSHOT')
@Grab('com.h2database:h2:2.5.252')
import jakarta.persistence.*

@Entity
class Author {
    @Id @GeneratedValue Long id
    String name
    @OneToMany(mappedBy = 'author') List<Book> books = []
}

@Entity
class Book {
    @Id @GeneratedValue Long id
    String title
    @ManyToOne Author author
}

def config = new PersistenceConfiguration('library')
    .provider('org.apache.openjpa.persistence.PersistenceProviderImpl')
    .managedClass(Author)
    .managedClass(Book)
    .property(PersistenceConfiguration.JDBC_DRIVER, 'org.h2.Driver')
    .property(PersistenceConfiguration.JDBC_URL, 'jdbc:h2:mem:library;DB_CLOSE_DELAY=-1')
    .property('openjpa.jdbc.SynchronizeMappings', 'buildSchema(ForeignKeys=true)')
    .property('openjpa.Log', 'DefaultLevel=WARN')
    // Script classes are loaded before the EMF exists, so no build-time or load-time enhancement:
    // fall back to OpenJPA's runtime subclassing (works, but to-one relations load eagerly)
    .property('openjpa.RuntimeUnenhancedClasses', 'supported')

def emf = config.createEntityManagerFactory()
println "Author enhanced? ${org.apache.openjpa.enhance.PersistenceCapable.isAssignableFrom(Author)}"

emf.runInTransaction { em ->
    def dierk = new Author(name: 'Dierk')
    em.persist(dierk)
    ['Groovy in Action', 'ReGinA'].each { em.persist(new Book(title: it, author: dierk)) }
}

def em = emf.createEntityManager()
em.createQuery('select b from Book b order by b.title', Book).resultList.each {
    println "$it.title by $it.author.name"
}
em.close()
emf.close()
