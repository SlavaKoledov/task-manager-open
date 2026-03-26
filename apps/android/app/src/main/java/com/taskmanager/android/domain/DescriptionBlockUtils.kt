package com.taskmanager.android.domain

import com.taskmanager.android.model.DescriptionBlock

private val checkboxPattern = Regex("""^\s*[-*]\s+\[( |x|X)]\s*(.*)$""")

fun createEmptyDescription(): List<DescriptionBlock> = listOf(DescriptionBlock.Text(""))

fun normalizeDescriptionBlocks(blocks: List<DescriptionBlock>): List<DescriptionBlock> {
    if (blocks.isEmpty()) {
        return createEmptyDescription()
    }

    return blocks.flatMap { block ->
        block.text.replace("\r\n", "\n").replace("\r", "\n").split("\n").map { line ->
            when (block) {
                is DescriptionBlock.Text -> DescriptionBlock.Text(text = line)
                is DescriptionBlock.Checkbox -> DescriptionBlock.Checkbox(text = line, checked = block.checked)
            }
        }
    }
}

fun stripDescriptionBlocks(blocks: List<DescriptionBlock>): List<DescriptionBlock> {
    val normalized = normalizeDescriptionBlocks(blocks)
    val trimIndex = normalized.indexOfLast { block ->
        when (block) {
            is DescriptionBlock.Text -> block.text.trim().isNotEmpty()
            is DescriptionBlock.Checkbox -> block.text.trim().isNotEmpty() || block.checked
        }
    }

    return if (trimIndex == -1) emptyList() else normalized.subList(0, trimIndex + 1)
}

fun descriptionBlocksToText(blocks: List<DescriptionBlock>): String? {
    val normalized = stripDescriptionBlocks(blocks)
    if (normalized.isEmpty()) {
        return null
    }

    val serialized = normalized.joinToString("\n") { block ->
        when (block) {
            is DescriptionBlock.Text -> block.text
            is DescriptionBlock.Checkbox -> "- [${if (block.checked) "x" else " "}] ${block.text}"
        }
    }

    return serialized.takeIf { it.trim().isNotEmpty() }
}

fun hasMeaningfulDescription(blocks: List<DescriptionBlock>, fallback: String? = null): Boolean =
    descriptionBlocksToText(ensureDescriptionBlocks(blocks, fallback)) != null

fun descriptionTextToBlocks(description: String?): List<DescriptionBlock> {
    if (description.isNullOrBlank()) {
        return createEmptyDescription()
    }

    return description.split(Regex("""\r?\n""")).map { line ->
        val checkboxMatch = checkboxPattern.matchEntire(line)
        if (checkboxMatch != null) {
            DescriptionBlock.Checkbox(
                text = checkboxMatch.groupValues[2],
                checked = checkboxMatch.groupValues[1].equals("x", ignoreCase = true),
            )
        } else {
            DescriptionBlock.Text(text = line)
        }
    }
}

fun ensureDescriptionBlocks(
    blocks: List<DescriptionBlock>?,
    fallback: String? = null,
): List<DescriptionBlock> = if (!blocks.isNullOrEmpty()) normalizeDescriptionBlocks(blocks) else descriptionTextToBlocks(fallback)
