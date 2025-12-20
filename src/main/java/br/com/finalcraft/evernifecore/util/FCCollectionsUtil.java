package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.MergeListResult;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FCCollectionsUtil {

    public static <T> List<T> reversed(List<T> list){
        Collections.reverse(list);
        return list;
    }

    /**
     * Splits a list of elements into a specified number of sublists, evenly distributing the elements.
     *
     * @param elements the list of elements to be split
     * @param parts    the number of sublists to split the list into
     * @return a list of sublists, each containing an equal distribution of elements
     *
     * @example:
     *    // Input: ["a", "b", "c", "d", "e", "f", "g", "h", "i", "j"], 4
     *    // Output: [["a", "b", "c"], ["d", "e"], ["f", "g", "h"], ["i", "j"]]
     * <p>
     *    // Input: ["a", "b"], 7
     *    // Output: [["a"], [], [], ["b"], [], [], []]
     * <p>
     *    // Input: ["a", "b"], 8
     *    // Output: [["a"], [], [], [], ["b"], [], [], []]
     * <p>
     *    // Input: ["a", "b"], 0
     *    // Output: []
     */
    public static <T> List<List<T>> partitionEvenly(List<T> elements, int parts){
        if (parts <= 0){
            return new ArrayList<>();
        }

        List<List<T>> result = new ArrayList<>(parts);

        if (parts == 1){
            result.add(new ArrayList<>(elements));
            return result;
        }

        int chunkSize = (int) Math.floor(elements.size() / (double) parts);
        int leftOver = (int) Math.floor(elements.size() % (double) parts);

        int gap = leftOver <= 0 ? 0 : parts / leftOver;
        int gapCount = gap;
        int gapNiddle = 0;

        for (int i = 0; i < parts; i++) {
            List<T> subList = new ArrayList<>();

            int start = (i * chunkSize) + gapNiddle;
            int end = start + chunkSize;

            if (chunkSize > 0){ //Evenly split these Chunks into even parts
                subList.addAll(elements.subList(start, end));
            }

            if (leftOver > 0){ //We still have some leftOver
                if (gapCount == gap){ //If we are into a gap position we add one more element
                    gapCount = 0;
                    leftOver--;
                    gapNiddle++; //We move the gapNiddle to the right to prevent element duplication
                    subList.add(elements.get(end));
                }
                gapCount++;
            }

            result.add(subList);
        }

        return result;
    }

    /**
     * Merges or updates an entity list with data from a DTO list, handling additions, updates, and removals.
     * This method synchronizes an existing entity collection with incoming DTO data by:
     * - Adding new entities for DTOs with null IDs
     * - Updating existing entities for DTOs with matching IDs
     * - Removing entities that are not present in the DTO list
     *
     * @param <ENTITY> The JPA entity type
     * @param <DTO> The DTO type containing update data
     * @param <ID> The ID type used for entity identification
     * @param existingEntities The current list of JPA entities to be synchronized (modified in-place)
     * @param incomingDtos The list of DTOs containing the desired state
     * @param entityIdGetter Function to extract ID from an entity
     * @param dtoIdGetter Function to extract ID from a DTO (null indicates new entity)
     * @param applyUpdates BiConsumer to update an entity with DTO data
     * @param newEntityFactory Function to create a new entity from a DTO
     *
     * @return JpaMergeListResult containing lists of updated, removed, and added entities
     * @throws IllegalArgumentException if a DTO references a non-existent entity ID
     *
     * @example
     * <pre>
     * // Example with User entities and UserDTO
     * List&lt;User&gt; users = getUsersFromDatabase();
     * List&lt;UserDTO&gt; userDTOs = getUpdatedUserData();
     *
     * JpaMergeListResult&lt;User&gt; result = mergeOrUpdateJPAListWithDTO(
     *     users,
     *     userDTOs,
     *     User::getId,           // Get ID from entity
     *     UserDTO::getId,        // Get ID from DTO (null for new users)
     *     (dto, entity) ->    {  // Update entity with DTO data
     *         entity.setName(dto.getName());
     *         entity.setEmail(dto.getEmail());
     *     },
     *     dto -> new User()     // Create new entity
     * );
     * </pre>
     */
    public static <ENTITY, DTO, ID> MergeListResult<ENTITY> updateListWithDTO(
            List<ENTITY> existingEntities,
            List<DTO> incomingDtos,
            Function<ENTITY, ID> entityIdGetter,
            Function<DTO, ID> dtoIdGetter,
            BiConsumer<DTO, ENTITY> applyUpdates,
            Function<DTO, ENTITY> newEntityFactory
    ) {
        Objects.requireNonNull(existingEntities, "existingEntities must not be null");
        Objects.requireNonNull(incomingDtos, "incomingDtos must not be null");

        List<ENTITY> added = new ArrayList<>();
        List<ENTITY> removed = new ArrayList<>();
        List<ENTITY> updated = new ArrayList<>();

        // 1 - Coleta IDs dos DTOs de entrada para comparação de remoção
        Set<ID> incomingIds = new HashSet<>();
        for (DTO dto : incomingDtos) {
            ID dtoId = dtoIdGetter.apply(dto);

            // Prevenir IDs duplicados nos DTOs de entrada
            if (dtoId != null && !incomingIds.add(dtoId)) {
                throw new IllegalArgumentException(String.format("There is a duplicated ID (%s) in the incoming DTOs List<%s>", dtoId, dto.getClass().getSimpleName()));
            }
        }

        // 2 - Remove entidades que não existem mais nos DTOs de entrada
        Iterator<ENTITY> iterator = existingEntities.iterator();
        while (iterator.hasNext()) {
            ENTITY entity = iterator.next();
            ID entityId = entityIdGetter.apply(entity);

            if (entityId != null && !incomingIds.contains(entityId)) {
                iterator.remove();
                removed.add(entity);
            }
        }

        // 3 - Mapeia entidades existentes restantes por ID para acesso rápido
        Map<ID, ENTITY> entitiesById = existingEntities.stream()
                .map(e -> new AbstractMap.SimpleEntry<>(entityIdGetter.apply(e), e))
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        // 4- Cria ou atualiza entidades com base nos DTOs de entrada
        for (DTO dto : incomingDtos) {
            ID dtoId = dtoIdGetter.apply(dto);

            if (dtoId == null) {
                // Adiciona nova entidade para DTOs sem ID
                ENTITY newEntity = newEntityFactory.apply(dto);
                applyUpdates.accept(dto, newEntity);

                existingEntities.add(newEntity);
                added.add(newEntity);
            } else {
                // Atualiza entidade existente para DTOs com ID
                ENTITY existing = entitiesById.get(dtoId);

                if (existing == null) {
                    throw new IllegalArgumentException(
                            "DTO refers to an entity that does not belong to the aggregate. ID='" + dtoId + "' \n Should be one of IDs: " + entitiesById.keySet()
                    );
                }
                applyUpdates.accept(dto, existing);
                updated.add(existing);
            }
        }

        return new MergeListResult<>(updated, removed, added);
    }

}
