package com.pydroidx.app

data class ConsoleSearchHit(
    val line: Int,
    val column: Int,
    val lineText: String
)

internal object ConsoleSearch {
    private const val DEFAULT_LIMIT = 150

    fun search(text: String, query: String, maxResults: Int = DEFAULT_LIMIT): List<ConsoleSearchHit> {
        val needle=query.trim()
        if (needle.isEmpty() || text.isEmpty() || maxResults<=0) return emptyList()
        val hits=ArrayList<ConsoleSearchHit>(minOf(maxResults,24))
        var lineNumber=1
        var lineStart=0
        var index=0
        while(index<=text.length && hits.size<maxResults) {
            val atEnd=index==text.length
            if(atEnd || text[index]=='\n') {
                val line=text.substring(lineStart,index)
                var from=0
                while(from<=line.length && hits.size<maxResults) {
                    val found=line.indexOf(needle,startIndex=from,ignoreCase=true)
                    if(found<0) break
                    hits += ConsoleSearchHit(lineNumber,found+1,line)
                    from=found+needle.length.coerceAtLeast(1)
                }
                lineNumber++
                lineStart=index+1
            }
            index++
        }
        return hits
    }
}
