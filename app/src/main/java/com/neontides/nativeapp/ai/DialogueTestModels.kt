package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.AiDialogueResult

data class BaseDialogueTestRecord(
    val characterId: String,
    val characterName: String,
    val question: String,
    val affection: Int,
    val attraction: Int,
    val trust: Int,
    val stage: String,
    val elapsedMs: Long,
    val preparationDiagnostic: String,
    val resourceDiagnostic: String,
    val changeConfirmed: Boolean? = null,
    val result: AiDialogueResult
)
