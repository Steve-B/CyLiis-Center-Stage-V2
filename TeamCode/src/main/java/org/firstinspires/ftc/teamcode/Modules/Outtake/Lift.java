package org.firstinspires.ftc.teamcode.Modules.Outtake;

import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.PIDCoefficients;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.Math.AsymmetricMotionProfile;
import org.firstinspires.ftc.teamcode.Modules.DriveModules.MecanumDrive;
import org.firstinspires.ftc.teamcode.Robot.Hardware;
import org.firstinspires.ftc.teamcode.Robot.IRobotModule;
import org.firstinspires.ftc.teamcode.Robot.IStateBasedModule;
import org.firstinspires.ftc.teamcode.Wrappers.CoolMotor;
import org.firstinspires.ftc.teamcode.Wrappers.Encoder;

@Config
public class Lift implements IStateBasedModule, IRobotModule {

    public static boolean ENABLED = true;

    public final CoolMotor leftMotor, rightMotor;
    public static boolean leftMotorReversed = true, rightMotorReversed = false;
    public final Encoder encoder;
    public static boolean encoderReversed = false;

    public static int groundPos = 0, firstLevel = 295, increment = 82, level = 0, positionThresh = 12,
            passthroughPosition = 250, purplePosition = 20;

    public static double resetPower = -0.5, maxHoldPower = -0.2, velocityThreshold = 0;

    public static PIDCoefficients pid = new PIDCoefficients(0.02,0.1,0.0006);
    public static double ff1 = 0.05, ff2 = 0.00003;

    private final ElapsedTime timer = new ElapsedTime();
    public static double timeOut = 0.15;

    public static double maxVelocity = 12000, acceleration = 10000, deceleration = 3500;
    public final AsymmetricMotionProfile profile = new AsymmetricMotionProfile(maxVelocity, acceleration, deceleration);
    public static boolean profiled = false;
    public static boolean defaultProfiledState = false;

    public enum State{
        DOWN(0), RESETTING(0, DOWN), GOING_DOWN(0, RESETTING),
        UP(firstLevel + increment * level), GOING_UP(firstLevel + increment * level, UP),
        PASSTHROUGH(passthroughPosition), GOING_PASSTHROUGH(passthroughPosition, PASSTHROUGH),
        PURPLE(purplePosition), GOING_PURPLE(purplePosition, PURPLE);

        public int position;
        public final State nextState;

        State(int position){
            this.position = position;
            this.nextState = this;
        }

        State(int position, State nextState){
            this.position = position;
            this.nextState = nextState;
        }
    }

    private State state;

    public State getState(){
        return state;
    }

    public void setState(State newState){
        updateStateValues();
        if(state == newState) return;
        profile.setMotion(profile.getPosition(), newState.position, profile.getSignedVelocity());
        timer.reset();
        this.state = newState;
    }

    private void updateStateValues(){
        State.UP.position = firstLevel + increment * level;
        State.GOING_UP.position = firstLevel + increment * level;
    }

    public Lift(Hardware hardware, State initialState){
        if(!ENABLED) leftMotor = null;
        else {
            leftMotor = new CoolMotor(hardware.meh1, CoolMotor.RunMode.PID, leftMotorReversed);
//            leftMotor.setZeroPowerBehaviour(DcMotor.ZeroPowerBehavior.BRAKE);
        }
        if(!ENABLED) rightMotor = null;
        else {
            rightMotor = new CoolMotor(hardware.meh2, CoolMotor.RunMode.PID, rightMotorReversed);
//            rightMotor.setZeroPowerBehaviour(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        if(!ENABLED) encoder = null;
        else {
            encoder = hardware.ech2;
            if(encoderReversed) encoder.setDirection(Encoder.Direction.REVERSE);
        }

        profiled = defaultProfiledState;

        timer.startTime();

        this.state = initialState;
    }

    @Override
    public void update() {
        if(!ENABLED) return;

        updateStateValues();
        updateState();
        updateHardware();
    }

    @Override
    public void updateState() {
        if(state == State.RESETTING){
            if(Math.abs(encoder.getRawVelocity()) <= velocityThreshold && timer.seconds() >= timeOut){
                groundPos = encoder.getCurrentPosition();
                updateStateValues();
                state = state.nextState;
            }
        }
        else if(state == State.GOING_DOWN && timer.seconds() >= timeOut){
            if(Math.abs(encoder.getRawVelocity()) <= velocityThreshold) setState(State.RESETTING);
        }
        else if(Math.abs((state.position + groundPos) - encoder.getCurrentPosition()) <= positionThresh)
            state = state.nextState;
    }

    public int target = 0;

    @Override
    public void updateHardware() {

        profile.update();

        if(state == State.RESETTING){
            leftMotor.setMode(CoolMotor.RunMode.RUN);
            rightMotor.setMode(CoolMotor.RunMode.RUN);
            leftMotor.setPower(resetPower);
            rightMotor.setPower(resetPower);
        }else {
            if(!profiled)target = state.position + groundPos;
            else target = (int)profile.getPosition() + groundPos;
            leftMotor.setMode(CoolMotor.RunMode.PID);
            rightMotor.setMode(CoolMotor.RunMode.PID);
            leftMotor.setPIDF(pid, ff1 + ff2 * (double) (target - groundPos));
            rightMotor.setPIDF(pid, ff1 + ff2 * (double) (target - groundPos));
            leftMotor.calculatePower(encoder.getCurrentPosition(), target);
            rightMotor.calculatePower(encoder.getCurrentPosition(), target);
            if(state == State.DOWN){
                leftMotor.power = Math.max(leftMotor.power, maxHoldPower);
                rightMotor.power = Math.max(rightMotor.power, maxHoldPower);
            }
        }
        leftMotor.update();
        rightMotor.update();

    }
}
