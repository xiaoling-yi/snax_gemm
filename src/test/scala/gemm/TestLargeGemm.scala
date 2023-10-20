package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// Random size of input matrices and Integer 8 data test and check with the results of largd Gemm with golden model
class LargeGemmRandomTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new LargeGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        // large gemm test generation function
        def LargeGemmRandomTets() = {

          // Randomly generate the size of the input matrices
          val (size_M, size_K, size_N) = MatrixLibLarge.GenRandSizeTest()
          // Randomly generation of input matrices
          val (matrix_A, matrix_B) =
            MatrixLibLarge.GenLargeMatrix(
              size_M,
              size_K,
              size_N
            )
          // Convert the sub-matrices to a big bus
          val (split_matrix_A, split_matrix_B) =
            MatrixLibLarge.SpliteLargeMatrx(
              size_M,
              size_K,
              size_N,
              matrix_A,
              matrix_B
            )
          val split_matrix_C =
            Array.ofDim[BigInt](size_M * size_N)
          // Random generation of the matrices start address
          val (start_addr_A, start_addr_B, start_addr_C) =
            MatrixLibLarge.GenRandSizeTest()
          // Generation of golden result in Scala
          val golden_array =
            MatrixLibLarge.LargeMarixMul_1D(
              size_M,
              size_K,
              size_N,
              matrix_A,
              matrix_B
            )

          // If the gemm_write_valid_o is asserted, take out the c_o data for check
          def CheckOutput() = {
            if (dut.io.ctrl.gemm_write_valid_o.peekBoolean()) {
              val addr_slide_C = (dut.io.ctrl.addr_c_o
                .peekInt() - start_addr_C) / GemmConstant.baseAddrIncrementC
              split_matrix_C(addr_slide_C.toInt) = dut.io.data.c_o.peekInt()
            }
          }

          // Give the large gemm configuration
          dut.clock.step(5)
          dut.io.ctrl.start_do_i.poke(false.B)
          dut.clock.step(5)
          dut.io.ctrl.start_do_i.poke(true.B)

          dut.io.ctrl.M_i.poke(size_M)
          dut.io.ctrl.K_i.poke(size_K)
          dut.io.ctrl.N_i.poke(size_N)

          dut.io.ctrl.ptr_addr_a_i.poke(start_addr_A)
          dut.io.ctrl.ptr_addr_b_i.poke(start_addr_B)
          dut.io.ctrl.ptr_addr_c_i.poke(start_addr_C)

          // If gemm_read_valid_o is asserted, give the right data_a_i and b_in data according to the address
          while (dut.io.ctrl.gemm_read_valid_o.peekBoolean()) {
            val addr_slide_A = (dut.io.ctrl.addr_a_o
              .peekInt() - start_addr_A) / GemmConstant.baseAddrIncrementA
            val addr_slide_B = (dut.io.ctrl.addr_b_o
              .peekInt() - start_addr_B) / GemmConstant.baseAddrIncrementB
            // println(addr_slide_A, addr_slide_B)
            dut.clock.step(1)
            dut.io.ctrl.start_do_i.poke(false.B)
            dut.io.ctrl.data_valid_i.poke(true.B)
            dut.io.data.a_i
              .poke(split_matrix_A(addr_slide_A.toInt))
            dut.io.data.b_i
              .poke(split_matrix_B(addr_slide_B.toInt))
            CheckOutput()
          }

          dut.clock.step(1)
          dut.io.ctrl.data_valid_i.poke(false.B)

          while (dut.io.ctrl.busy_o.peekBoolean()) {
            CheckOutput()
            dut.clock.step(1)
          }

          dut.clock.step(5)
          // Compare the output data with the golden model
          MatrixLibBase.CheckResults(
            size_M * size_N,
            split_matrix_C,
            golden_array
          )
        }

        dut.clock.step()
        val TestLoop = 2

        for (i <- 0 until TestLoop) {
          LargeGemmRandomTets()
        }

        emitVerilog(
          new (LargeGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// Simple large Gemm test to see if the control signals work well
class LargeGemmBaseTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new LargeGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(2)
        dut.io.ctrl.N_i.poke(2)
        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(5)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(3)
        dut.io.ctrl.N_i.poke(4)
        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(21)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(1)
        dut.io.ctrl.K_i.poke(4)
        dut.io.ctrl.N_i.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(4)
        dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(1)
        dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(4)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        emitVerilog(
          new (LargeGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// simple test to see if the LargeGemmController work well with manual configuration
class LargeGemmControllerTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new LargeGemmController)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(5)

        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(2)
        dut.io.K_i.poke(2)
        dut.io.N_i.poke(2)
        dut.io.ptr_addr_a_i.poke(2)
        dut.io.ptr_addr_b_i.poke(3)
        dut.io.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(5)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(1)
        dut.io.K_i.poke(4)
        dut.io.N_i.poke(1)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(4)
        dut.io.K_i.poke(1)
        dut.io.N_i.poke(1)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(1)
        dut.io.K_i.poke(1)
        dut.io.N_i.poke(4)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        emitVerilog(
          new (LargeGemmController),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}
