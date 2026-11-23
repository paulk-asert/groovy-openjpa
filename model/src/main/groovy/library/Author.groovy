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
package library

import jakarta.persistence.*

// A plain JPA entity, shared by the openjpa, grails-data and mixed subprojects.
// With Grails Data on the compile classpath, its global AST transform makes this a GORM entity.
@Entity
class Author {
    @Id @GeneratedValue
    Long id

    String name

    @OneToMany(mappedBy = 'author', fetch = FetchType.LAZY)
    List<Book> books = []
}
