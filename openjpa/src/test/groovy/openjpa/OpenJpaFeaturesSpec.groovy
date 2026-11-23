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

import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.apache.openjpa.enhance.PersistenceCapable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class OpenJpaFeaturesSpec extends Specification {
    @Shared EntityManagerFactory emf = Persistence.createEntityManagerFactory('library')
    @Shared Long writerId, novelId

    def cleanupSpec() { emf?.close() }

    def "groovyc output was enhanced"() {
        expect:
        [Writer, Novel, Gadget].every { PersistenceCapable.isAssignableFrom(it) }
    }

    def "round trip via JPA 3.2 runInTransaction with a closure"() {
        when:
        emf.runInTransaction { em ->
            def a = new Writer(name: 'Dierk')
            em.persist(a)
            def b = new Novel(title: 'Groovy in Action', writer: a, price: new Price(59.99, 'USD'))
            em.persist(b)
            em.persist(new Novel(title: 'ReGinA', writer: a, price: new Price(44.95, 'USD')))
            em.persist(new Gadget(label: 'widget'))
            writerId = a.id
            novelId = b.id
        }
        def title = emf.callInTransaction { em -> em.find(Novel, novelId).title }

        then:
        writerId && novelId
        title == 'Groovy in Action'
    }

    def "callInTransaction returns non-entity results"() {
        when:
        def result = emf.callInTransaction { em ->
            em.persist(new Gadget(label: 'committed'))
            'some string'
        }

        then:
        result == 'some string'
        emf.callInTransaction { em -> em.createQuery("select count(g) from Gadget g where g.label = 'committed'", Long).singleResult } == 1L

        cleanup:
        emf.runInTransaction { em -> em.createQuery("delete from Gadget g where g.label = 'committed'").executeUpdate() }
    }

    def "record embeddable survives a round trip"() {
        expect:
        emf.callInTransaction { em -> em.find(Novel, novelId).price } == new Price(59.99, 'USD')
    }

    def "property access entity ignores metaClass"() {
        expect:
        emf.callInTransaction { em -> em.createQuery('select g.label from Gadget g', String).singleResult } == 'widget'
    }

    def "lazy ManyToOne loads on access from @CompileStatic entity"() {
        expect:
        emf.callInTransaction { em ->
            def util = emf.persistenceUnitUtil
            def b = em.find(Novel, novelId)
            def before = util.isLoaded(b, 'writer')
            def name = b.writer.name
            [before, name, util.isLoaded(b, 'writer')]
        } == [false, 'Dierk', true]
    }

    def "lazy OneToMany loads from inside a dynamic Groovy method"() {
        expect:
        emf.callInTransaction { em ->
            def util = emf.persistenceUnitUtil
            def a = em.find(Writer, writerId)
            def before = util.isLoaded(a, 'novels')
            def count = a.novelCount()
            [before, count, util.isLoaded(a, 'novels')]
        } == [false, 2, true]
    }

    def "lazy OneToMany via direct field access (obj.@field) from outside"() {
        expect:
        emf.callInTransaction { em ->
            def a = em.find(Writer, writerId)
            def raw = a.@novels
            [raw == null ? 'null' : raw.size(), a.novels.size()].tap { println "obj.@novels: $it" }
        } == ['null', 2] // obj.@field bypasses the enhancer's interception: lazy field never loaded
    }

    def "dirty tracking: property set vs direct field set"() {
        when:
        emf.runInTransaction { em -> em.find(Writer, writerId).name = 'Dierk König' }
        def viaProperty = emf.callInTransaction { em -> em.find(Writer, writerId).name }

        emf.runInTransaction { em -> em.find(Writer, writerId).@name = 'D. König' }
        emf.getCache().evictAll()
        def viaField = emf.callInTransaction { em ->
            em.createQuery('select a.name from Writer a where a.id = :id', String)
              .setParameter('id', writerId).singleResult
        }

        then:
        viaProperty == 'Dierk König'
        println "viaField: $viaField"; viaField == 'Dierk König' // obj.@field = ... bypasses dirty tracking: the update is silently lost
    }

    def "JPQL 3.2 additions: || concat, left()"() {
        expect:
        emf.callInTransaction { em ->
            em.createQuery("select left(b.title, 6) || ':' || b.price.currency from Novel b order by b.title", String)
              .resultList
        } == ['Groovy:USD', 'ReGinA:USD']

    }

    def "JPQL 3.2 set operations: typed scalars, in both string and criteria form"() {
        given:
        def jpql = 'select g.label from Gadget g union all select g.label from Gadget g'

        expect:
        emf.callInTransaction { em -> em.createQuery(jpql, String).resultList } == ['widget', 'widget']
        emf.callInTransaction { em ->
            def cb = em.criteriaBuilder
            def select = {
                def q = cb.createQuery(String)
                q.select(q.from(Gadget).get('label'))
            }
            em.createQuery(cb.unionAll(select(), select())).resultList
        } == ['widget', 'widget']
    }
}
