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

import jakarta.persistence.*

// Property access: does OpenJPA treat getMetaClass()/setMetaClass() as a persistent property?
@Entity
@Access(AccessType.PROPERTY)
class Gadget {
    private Long id
    private String label

    @Id @GeneratedValue
    Long getId() { id }
    void setId(Long id) { this.id = id }

    String getLabel() { label }
    void setLabel(String label) { this.label = label }
}
