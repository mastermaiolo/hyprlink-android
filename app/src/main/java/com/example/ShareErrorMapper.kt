// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

object ShareErrorMapper {

    const val DEFAULT_REFUSED = "O PC recusou o ficheiro"

    /**
     * Mapeia os motivos de recusa conhecidos enviados pelo daemon Rust do PC
     * para mensagens legíveis em pt-PT (ou no idioma da app via AppStrings).
     * Motivos desconhecidos são mapeados para "O PC recusou o ficheiro".
     */
    fun mapShareError(rawError: String?, strings: AppStrings = LanguageManager.currentStrings): String {
        if (rawError.isNullOrBlank()) {
            return strings.pcRefusedFile
        }

        val trimmed = rawError.trim()

        return when {
            trimmed.startsWith("excede o limite de", ignoreCase = true) ->
                "Ficheiro excede o limite permitido pelo PC"

            trimmed.startsWith("sem espaço no destino", ignoreCase = true) ->
                "Sem espaço livre no disco do PC"

            trimmed.contains("pasta de destino indisponível", ignoreCase = true) ->
                "Pasta de destino indisponível no PC"

            trimmed.contains("não foi possível criar o ficheiro", ignoreCase = true) ->
                "Não foi possível criar o ficheiro no PC"

            trimmed.contains("cancelado pelo utilizador", ignoreCase = true) ->
                "Transferência cancelada no PC"

            trimmed.contains("excedeu o tamanho anunciado", ignoreCase = true) ->
                "Ficheiro excedeu o tamanho anunciado"

            trimmed.startsWith("incompleto:", ignoreCase = true) ->
                trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            trimmed.contains("a ligação falhou a meio", ignoreCase = true) ->
                "A ligação com o PC falhou a meio"

            trimmed.contains("erro ao escrever no disco", ignoreCase = true) ->
                "Erro ao escrever no disco do PC"

            trimmed.contains("sem dados há demasiado tempo", ignoreCase = true) ||
            trimmed.contains("tempo esgotado", ignoreCase = true) ->
                "Tempo esgotado (sem dados há mais de 60 s)"

            trimmed.contains("não foi possível gravar", ignoreCase = true) ->
                "Não foi possível gravar o ficheiro no PC"

            else -> strings.pcRefusedFile
        }
    }

    /**
     * Formata o aviso de nome final guardado no PC quando share.done traz o campo opcional 'name'.
     */
    fun formatSavedOnPcAs(savedName: String, strings: AppStrings = LanguageManager.currentStrings): String {
        return String.format(strings.savedOnPcAs, savedName)
    }
}
