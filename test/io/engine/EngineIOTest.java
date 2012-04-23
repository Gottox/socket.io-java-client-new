package io.engine;

import static org.junit.Assert.*;

import java.util.LinkedList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class EngineIOTest extends EngineIO {
	abstract String getServerEvent();

	
}
