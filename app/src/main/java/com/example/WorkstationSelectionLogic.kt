// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

object WorkstationSelectionLogic {
    data class ResolutionResult(
        val activeWorkstation: PairedWorkstation?,
        val resolvedId: String,
        val shouldOpenPairing: Boolean,
        val isLoading: Boolean = false
    )

    /**
     * Resolve a estação de trabalho ativa com segurança, distinguindo o estado de carregamento
     * do Room (workstations == null) de uma lista vazia carregada (workstations.isEmpty()).
     *
     * Regras:
     * 1. Se workstations for null (a carregar do Room): não resolve, não altera preferências,
     *    preserva o storedActiveId e shouldOpenPairing = false.
     * 2. Se a lista estiver vazia (carregada com 0 estações): activeWorkstation = null, resolvedId = "",
     *    shouldOpenPairing = true para conduzir o utilizador ao emparelhamento.
     * 3. Se a lista contiver estações e storedActiveId corresponder a uma delas, seleciona-a.
     * 4. Se a lista contiver estações mas storedActiveId for nulo, em branco ou não existir,
     *    faz fallback seguro para a primeira estação disponível.
     */
    fun resolveActiveWorkstation(
        workstations: List<PairedWorkstation>?,
        storedActiveId: String?
    ): ResolutionResult {
        if (workstations == null) {
            return ResolutionResult(
                activeWorkstation = null,
                resolvedId = storedActiveId ?: "",
                shouldOpenPairing = false,
                isLoading = true
            )
        }
        if (workstations.isEmpty()) {
            return ResolutionResult(
                activeWorkstation = null,
                resolvedId = "",
                shouldOpenPairing = true,
                isLoading = false
            )
        }
        val match = workstations.find { it.id == storedActiveId }
        if (match != null) {
            return ResolutionResult(
                activeWorkstation = match,
                resolvedId = match.id,
                shouldOpenPairing = false,
                isLoading = false
            )
        }
        val fallback = workstations.first()
        return ResolutionResult(
            activeWorkstation = fallback,
            resolvedId = fallback.id,
            shouldOpenPairing = false,
            isLoading = false
        )
    }
}
