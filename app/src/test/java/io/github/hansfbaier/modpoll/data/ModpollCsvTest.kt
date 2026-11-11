package io.github.hansfbaier.modpoll.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModpollCsvTest {
    @Test
    fun genericProfileHasNoRegisterMap() {
        val generic = BuiltInProfiles.generic()
        assertTrue(generic.groups.isEmpty())
        assertEquals(0, generic.signalCount)
        assertTrue(BuiltInProfiles.all().first() == generic)
    }

    private val sample =
        """
        # example
        device,test01,1,,
        poll,coil,0,16,BE_BE
        ref,coil01-08,0,bool8,rw
        ref,coil09-16,1,bool8,rw
        poll,holding_register,40000,4,BE_BE
        ref,hr1,40000,uint16,rw
        ref,hr2,40001,int16,r
        ref,hr3,40002,uint32,r,,0.001
        ref,flag,40003:3,bool,r
        """.trimIndent()

    @Test
    fun importsModpollCsv() {
        val result = ModpollCsv.import(sample)
        assertEquals(1, result.profiles.size)
        val profile = result.profiles.single()
        assertEquals("test01", profile.name)
        assertEquals(1, profile.unitId)
        assertEquals(2, profile.groups.size)

        val coils = profile.groups.first { it.objectType == ObjectType.COIL }
        assertEquals(2, coils.signals.size)
        assertEquals(0, coils.signals[0].address)
        assertEquals(DataType.BOOL8, coils.signals[0].dataType)
        assertEquals(8, coils.signals[1].address)
        assertTrue(coils.signals[0].writable)

        val holding = profile.groups.first { it.objectType == ObjectType.HOLDING_REGISTER }
        assertEquals(4, holding.signals.size)
        val scaled = holding.signals.first { it.name == "hr3" }
        assertEquals(0.001, scaled.scale, 0.0)
        assertEquals(DataType.UINT32, scaled.dataType)
        val flag = holding.signals.first { it.name == "flag" }
        assertEquals(DataType.BOOL, flag.dataType)
        assertEquals(3, flag.bit)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun rejectsRefOutsidePollRange() {
        val csv =
            """
            device,dev,1,,
            poll,holding_register,100,2,BE_BE
            ref,bad,105,uint16,r
            """.trimIndent()
        val result = ModpollCsv.import(csv)
        assertTrue(result.profiles.isEmpty() || result.profiles.single().signalCount == 0)
        assertTrue(result.warnings.any { it.contains("exceeds poll range") })
    }

    @Test
    fun importsMultipleDevices() {
        val csv =
            """
            device,one,1,,
            poll,holding_register,0,2,BE_BE
            ref,a,0,uint16,r
            device,two,2,,
            poll,input_register,10,2,BE_BE
            ref,b,10,float32,r
            """.trimIndent()
        val result = ModpollCsv.import(csv)
        assertEquals(2, result.profiles.size)
        assertEquals(2, result.profiles[1].unitId)
        assertEquals(1, result.profiles[1].signalCount)
    }

    @Test
    fun stringLengthIsConvertedToRegisters() {
        val csv =
            """
            device,dev,1,,
            poll,holding_register,0,10,BE_BE
            ref,name,0,string16,r
            """.trimIndent()
        val profile = ModpollCsv.import(csv).profiles.single()
        val signal =
            profile.groups
                .single()
                .signals
                .single()
        assertEquals(DataType.STRING, signal.dataType)
        assertEquals(16, signal.stringBytes)
        assertEquals(8, signal.registerSpan)
    }

    @Test
    fun exportProducesReimportableCsv() {
        val profile = ModpollCsv.import(sample).profiles.single()
        val (csv, warnings) = ModpollCsv.export(profile)
        assertTrue(warnings.isEmpty())
        assertTrue(csv.contains("device,test01,1"))
        assertTrue(csv.contains("poll,holding_register,40000,4,BE_BE"))
        val reimported = ModpollCsv.import(csv).profiles.single()
        assertEquals(profile.signalCount, reimported.signalCount)
        assertEquals(profile.groups.size, reimported.groups.size)
        val flag = reimported.groups.flatMap { it.signals }.first { it.name == "flag" }
        assertNotNull(flag.bit)
        assertEquals(3, flag.bit)
    }

    @Test
    fun bitGroupOffsetUsesPollStart() {
        val csv =
            """
            device,dev,1,,
            poll,coil,8,16,BE_BE
            ref,first,8,bool8,rw
            ref,second,9,bool8,rw
            """.trimIndent()
        val group = ModpollCsv.import(csv).profiles.single().groups.single()
        assertEquals(8, group.signals[0].address)
        assertEquals(16, group.signals[1].address)
        val (exported, warnings) = ModpollCsv.export(ModpollCsv.import(csv).profiles.single())
        assertTrue(warnings.isEmpty())
        assertTrue(exported.contains("ref,first,8,bool8,rw"))
        assertTrue(exported.contains("ref,second,9,bool8,rw"))
    }

    @Test
    fun invalidTypeIsReported() {
        val csv =
            """
            device,dev,1,,
            poll,holding_register,0,2,BE_BE
            ref,x,0,complex128,r
            """.trimIndent()
        val result = ModpollCsv.import(csv)
        assertTrue(result.warnings.any { it.contains("unsupported data type") })
    }
}
