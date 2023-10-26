package gemm
import chisel3._
import chisel3.util._

// The LargeGemmControllerIO's port declaration. Detailed explanation of these ports can be found in the README
class LargeGemmControllerIO extends Bundle {
  val M_i = Input(UInt(8.W))
  val K_i = Input(UInt(8.W))
  val N_i = Input(UInt(8.W))
  val start_do_i = Input(Bool())
  val data_valid_o = Input(Bool())
  val data_valid_i = Input(Bool())

  val ptr_addr_a_i = Input(UInt(32.W))
  val ptr_addr_b_i = Input(UInt(32.W))
  val ptr_addr_c_i = Input(UInt(32.W))

  val gemm_read_valid_o = Output(Bool())
  val gemm_write_valid_o = Output(Bool())

  val addr_a_o = Output(UInt(32.W))
  val addr_b_o = Output(UInt(32.W))
  val addr_c_o = Output(UInt(32.W))

  val busy_o = Output(Bool())
  val accumulate_i = Output(Bool())
}

// LargeGemmController module. This module takes in the configurations and gives out read/write valid signals and the right addresses of the sub-matrices.
class LargeGemmController extends Module {
  lazy val io = IO(new LargeGemmControllerIO())

  val start_do = RegInit(false.B)

  val M = RegInit(0.U(8.W))
  val K = RegInit(0.U(8.W))
  val N = RegInit(0.U(8.W))

  val ptr_addr_a = RegInit(0.U(32.W))
  val ptr_addr_b = RegInit(0.U(32.W))
  val ptr_addr_c = RegInit(0.U(32.W))

  // Counters for tracing the block matrix multiplication and generating the right addresses
  val M_read_counter = WireInit(0.U(8.W))
  val N_read_counter = WireInit(0.U(8.W))
  val K_read_counter = WireInit(0.U(8.W))

  val M_write_counter = WireInit(0.U(8.W))
  val N_write_counter = WireInit(0.U(8.W))
  val K_write_counter = WireInit(0.U(8.W))

  val read_tcdm_done = WireInit(false.B)
  val gemm_done = WireInit(false.B)

  val read_counter = RegInit(0.U(24.W))
  val write_counter = RegInit(0.U(24.W))
  val write_counter_next = RegInit(0.U(24.W))

  val write_valid_counter = RegInit(0.U(8.W))
  val accumulate_counter = RegInit(0.U(8.W))

  // State declaration
  val sIDLE :: sREAD :: sREAD_DONE :: Nil = Enum(3)
  val cstate = RegInit(sIDLE)
  val nstate = WireInit(sIDLE)

  // Changing states
  cstate := nstate

  // Next state changes
  switch(cstate) {
    is(sIDLE) {
      when(io.start_do_i) {
        nstate := sREAD
      }.otherwise {
        nstate := sIDLE
      }
    }
    is(sREAD) {
      when(read_tcdm_done) {
        nstate := sREAD_DONE
      }.otherwise {
        nstate := sREAD
      }
    }
    is(sREAD_DONE) {
      when(gemm_done) {
        nstate := sIDLE
      }.otherwise {
        nstate := sREAD_DONE
      }
    }
  }

  // Store the configurations
  when(io.start_do_i && !io.busy_o) {
    M := io.M_i
    N := io.N_i
    K := io.K_i
    ptr_addr_a := io.ptr_addr_a_i
    ptr_addr_b := io.ptr_addr_b_i
    ptr_addr_c := io.ptr_addr_c_i
  }.elsewhen(cstate === sIDLE) {
    M := 0.U
    N := 0.U
    K := 0.U
    ptr_addr_a := 0.U
    ptr_addr_b := 0.U
    ptr_addr_c := 0.U
  }

  when(io.start_do_i && !io.busy_o) {
    start_do := true.B
  }.otherwise {
    start_do := false.B
  }

  when(start_do) {
    read_counter := read_counter + 1.U
  }.elsewhen(read_counter === M * N * K) {
    read_counter := 0.U
  }.elsewhen(io.data_valid_i && cstate === sREAD) {
    read_counter := read_counter + 1.U
  }.elsewhen(cstate === sIDLE) {
    read_counter := 0.U
  }

  when(io.data_valid_o) {
    write_counter_next := write_counter_next + 1.U
  }.elsewhen(cstate === sIDLE) {
    write_counter_next := 0.U
  }

  when(io.data_valid_o) {
    write_counter := write_counter_next
  }.elsewhen(cstate === sIDLE) {
    write_counter := 0.U
  }

  M_read_counter := read_counter / (K * N)
  K_read_counter := (read_counter % (K * N)) % K
  N_read_counter := (read_counter % (K * N)) / K

  M_write_counter := (write_counter / K) / N
  K_write_counter := write_counter % K
  N_write_counter := (write_counter / K) % N

  // write_counter for the address generation to write submatrix of C
  when(
    io.data_valid_o && write_valid_counter =/= K && cstate =/= sIDLE
  ) {
    write_valid_counter := write_valid_counter + 1.U
  }.elsewhen(
    io.data_valid_o && write_valid_counter === K && cstate =/= sIDLE
  ) {
    write_valid_counter := 1.U
  }.elsewhen(write_valid_counter === K || cstate === sIDLE) {
    write_valid_counter := 0.U
  }

  // accumulate_counter for geenrating the accumulation signal for BaseGemm
  when(
    (io.data_valid_i === 1.B) && accumulate_counter =/= K - 1.U && K =/= 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := accumulate_counter + 1.U
  }.elsewhen(
    (io.data_valid_i === 1.B) && accumulate_counter === K - 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := 0.U
  }.elsewhen(
    write_valid_counter === K - 1.U || cstate === sIDLE
  ) {
    accumulate_counter := 0.U
  }

  // Intermediate or output control signals generation according to the counters
  read_tcdm_done := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && io.gemm_read_valid_o
  gemm_done := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && io.gemm_write_valid_o

  io.gemm_read_valid_o := (start_do === 1.B) || (io.data_valid_i && cstate === sREAD)
  io.gemm_write_valid_o := (write_valid_counter === K) && cstate =/= sIDLE

  io.busy_o := (cstate =/= sIDLE)
  io.accumulate_i := (accumulate_counter =/= K - 1.U && (io.data_valid_i === 1.B))

  // Address generation
  io.addr_a_o := ptr_addr_a + (GemmConstant.baseAddrIncrementA.U) * (M_read_counter * K + K_read_counter)
  io.addr_b_o := ptr_addr_b + (GemmConstant.baseAddrIncrementB.U) * (N_read_counter * K + K_read_counter)
  io.addr_c_o := ptr_addr_c + (GemmConstant.baseAddrIncrementC.U) * (M_write_counter * N + N_write_counter)

}

// The LargeGemm's control port declaration. Detailed explanation of these ports can be found in the README
class LargeGemmCtrlIO extends Bundle {
  val M_i = Input(UInt(8.W))
  val K_i = Input(UInt(8.W))
  val N_i = Input(UInt(8.W))

  val start_do_i = Input(Bool())
  val data_valid_i = Input(Bool())

  val ptr_addr_a_i = Input(UInt(32.W))
  val ptr_addr_b_i = Input(UInt(32.W))
  val ptr_addr_c_i = Input(UInt(32.W))

  val gemm_read_valid_o = Output(Bool())
  val gemm_write_valid_o = Output(Bool())

  val addr_a_o = Output(UInt(32.W))
  val addr_b_o = Output(UInt(32.W))
  val addr_c_o = Output(UInt(32.W))

  val busy_o = Output(Bool())
}

// LargeGemmIO definition
class LargeGemmIO extends Bundle {
  val data = new GemmDataIO()
  val ctrl = new LargeGemmCtrlIO()
}

// LargeGemm module.
// In this module, a GemmArray is generated to do the computation and
// a controller is generated to generate the control signals for
// read/write reqeust and related address
class LargeGemm extends Module {
  lazy val io = IO(new LargeGemmIO())

  val gemm_array = Module(new GemmArray())
  lazy val controller = Module(new LargeGemmController())

  controller.io.M_i := io.ctrl.M_i
  controller.io.K_i := io.ctrl.K_i
  controller.io.N_i := io.ctrl.N_i
  controller.io.start_do_i := io.ctrl.start_do_i
  controller.io.data_valid_i := io.ctrl.data_valid_i
  controller.io.ptr_addr_a_i := io.ctrl.ptr_addr_a_i
  controller.io.ptr_addr_b_i := io.ctrl.ptr_addr_b_i
  controller.io.ptr_addr_c_i := io.ctrl.ptr_addr_c_i
  controller.io.gemm_read_valid_o := io.ctrl.gemm_read_valid_o
  controller.io.gemm_write_valid_o := io.ctrl.gemm_write_valid_o
  controller.io.addr_a_o := io.ctrl.addr_a_o
  controller.io.addr_b_o := io.ctrl.addr_b_o
  controller.io.addr_c_o := io.ctrl.addr_c_o
  controller.io.busy_o := io.ctrl.busy_o
  controller.io.data_valid_o := gemm_array.io.data_valid_o

  gemm_array.io.data.a_i := io.data.a_i
  gemm_array.io.data.b_i := io.data.b_i
  io.data.c_o := RegNext(gemm_array.io.data.c_o)

  gemm_array.io.data_valid_i := io.ctrl.data_valid_i
  gemm_array.io.accumulate_i := controller.io.accumulate_i
}
