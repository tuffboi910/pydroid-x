package com.pydroidx.app

import java.io.File
import java.io.IOException

data class TrashedProjectFile(
    val trashName: String,
    val originalName: String,
    val deletedAt: Long
)

internal object ProjectFileTrash {
    private const val SEP = "__"

    fun safePythonFileName(value: String): String {
        val withoutExtension=value.trim().removeSuffix(".py").removeSuffix(".PY")
        val base=withoutExtension
            .replace(Regex("[^A-Za-z0-9 _-]+")," ")
            .replace(Regex(" +")," ")
            .trim(' ','.','_','-')
            .take(48)
            .ifBlank { "untitled" }
        return "$base.py"
    }

    fun duplicateTarget(projectDir: File, source: File): File {
        val base=source.nameWithoutExtension.ifBlank { "file" }
        var target=File(projectDir,"${base}_copy.py")
        var number=2
        while(target.exists()) target=File(projectDir,"${base}_copy_${number++}.py")
        return target
    }

    fun list(projectDir: File): List<TrashedProjectFile> {
        val trashDir=File(projectDir,".trash")
        return trashDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && SEP in it.name }
            ?.mapNotNull { file ->
                val split=file.name.indexOf(SEP)
                val original=file.name.substring(split+SEP.length)
                if(original.isBlank()) null else TrashedProjectFile(
                    trashName=file.name,
                    originalName=original,
                    deletedAt=file.name.substring(0,split).toLongOrNull() ?: file.lastModified()
                )
            }
            ?.sortedByDescending { it.deletedAt }
            ?.toList()
            .orEmpty()
    }

    fun move(source: File): File {
        if(!source.isFile) throw IOException("File no longer exists")
        val projectDir=source.parentFile ?: throw IOException("File has no project directory")
        val trashDir=File(projectDir,".trash")
        if(!trashDir.isDirectory && !trashDir.mkdirs()) throw IOException("Could not create Trash")

        var stamp=System.currentTimeMillis()
        var target=File(trashDir,"$stamp$SEP${source.name}")
        while(target.exists()) {
            stamp++
            target=File(trashDir,"$stamp$SEP${source.name}")
        }
        if(!source.renameTo(target)) throw IOException("Could not move ${source.name} to Trash")

        val oldHistory=File(File(projectDir,".history"),source.name)
        if(oldHistory.isDirectory) {
            val trashHistoryRoot=File(trashDir,".history")
            if(!trashHistoryRoot.isDirectory && !trashHistoryRoot.mkdirs()) {
                target.renameTo(source)
                throw IOException("Could not preserve file history")
            }
            val trashHistory=File(trashHistoryRoot,target.name)
            if(!oldHistory.renameTo(trashHistory)) {
                target.renameTo(source)
                throw IOException("Could not preserve file history")
            }
        }
        return target
    }

    fun restore(projectDir: File, trashName: String): File {
        val trashDir=File(projectDir,".trash")
        val source=File(trashDir,trashName)
        if(!source.isFile || source.parentFile?.canonicalFile != trashDir.canonicalFile) {
            throw IOException("Deleted file is no longer available")
        }
        val split=source.name.indexOf(SEP)
        if(split<0) throw IOException("Invalid Trash entry")
        val original=source.name.substring(split+SEP.length)
        var target=File(projectDir,original)
        if(target.exists()) {
            val extension=target.extension
            val base=target.nameWithoutExtension
            var number=1
            do {
                val suffix=if(number==1) "_restored" else "_restored_$number"
                target=File(projectDir,if(extension.isBlank()) "$base$suffix" else "$base$suffix.$extension")
                number++
            } while(target.exists())
        }
        if(!source.renameTo(target)) throw IOException("Could not restore $original")

        val trashHistory=File(File(trashDir,".history"),source.name)
        if(trashHistory.isDirectory) {
            val historyRoot=File(projectDir,".history")
            if(!historyRoot.isDirectory && !historyRoot.mkdirs()) {
                target.renameTo(source)
                throw IOException("Could not restore file history")
            }
            val restoredHistory=File(historyRoot,target.name)
            if(restoredHistory.exists() || !trashHistory.renameTo(restoredHistory)) {
                target.renameTo(source)
                throw IOException("Could not restore file history")
            }
        }
        return target
    }
}
