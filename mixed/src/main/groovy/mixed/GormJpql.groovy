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

import grails.gorm.DetachedCriteria
import jakarta.persistence.EntityManager
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.jpa.JpaQueryBuilder
import org.grails.datastore.mapping.query.jpa.JpaQueryInfo

/**
 * Runs GORM where queries and criteria on a JPA EntityManager, using GORM's
 * own JPQL builder. GORM supplies the query model and the JPQL; OpenJPA
 * executes it.
 */
class GormJpql {

    private static final ILIKE_PATTERN = ~/like lower\(:p(\d+)\)/

    static JpaQueryInfo translate(DetachedCriteria<?> criteria) {
        def projections = new Query.ProjectionList()
        criteria.projections.each { projections.add(it) }
        def entity = criteria.persistentEntity
        def builder = new JpaQueryBuilder(entity, criteria.criteria, projections, criteria.orders)
        builder.conversionService = entity.mappingContext.conversionService
        def info = builder.buildSelect()
        def jpql = info.query
        def parameters = info.parameters == null ? [] : new ArrayList(info.parameters)

        // JPQL only allows a parameter or literal as a LIKE pattern
        jpql.findAll(ILIKE_PATTERN) { all, position ->
            int index = position.toInteger() - 1
            parameters[index] = parameters[index].toString().toLowerCase()
        }
        jpql = jpql.replaceAll(ILIKE_PATTERN, 'like :p$1')

        // the builder has no case for a distinct property projection
        if (criteria.projections.any { it instanceof Query.DistinctPropertyProjection }) {
            jpql = jpql.replaceFirst('^SELECT ', 'SELECT DISTINCT ')
        }
        new JpaQueryInfo(jpql, parameters)
    }

    static List list(EntityManager em, DetachedCriteria<?> criteria) {
        def info = translate(criteria)
        def query = em.createQuery(info.query)
        info.parameters.eachWithIndex { value, i -> query.setParameter("p${i + 1}", value) }
        if (criteria.@defaultMax != null) query.maxResults = criteria.@defaultMax
        if (criteria.@defaultOffset != null) query.firstResult = criteria.@defaultOffset
        query.resultList
    }
}
